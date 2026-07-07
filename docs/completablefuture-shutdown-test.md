# CompletableFuture shutdown test

## Conclusion

`CompletableFuture` does not directly receive a server restart signal.

For Spring Boot applications, use Spring/JVM lifecycle signals to mark in-memory async tasks as retryable before the process exits:

- `ContextClosedEvent`
- `@PreDestroy`
- JVM shutdown hook if the code is not managed by Spring
- external persistent state table for task status

The key design is not "let CompletableFuture survive restart". The key design is "task state is persisted outside JVM; JVM shutdown only changes RUNNING to RETRYABLE".

## Demo branch

Branch:

```bash
test-completablefuture-shutdown
```

## Run

```bash
mvn spring-boot:run
```

## Start a task

```bash
curl -X POST http://localhost:8080/async-tasks \
  -H 'Content-Type: application/json' \
  -d '{"payload":"demo"}'
```

## Query task state

```bash
curl http://localhost:8080/async-tasks
```

Expected intermediate state:

```json
[
  {
    "status": "RUNNING",
    "progress": 10
  }
]
```

## Simulate Spring context shutdown

```bash
curl -X POST http://localhost:8080/async-tasks/simulate-context-close
```

Expected effect:

- Spring `ContextClosedEvent` is published.
- Running tasks are marked `RETRYABLE` in H2.
- JVM process exits after Spring context closes.

Restart:

```bash
mvn spring-boot:run
curl http://localhost:8080/async-tasks
```

Expected effect:

- Startup recovery marks stale `RUNNING` tasks as `RETRYABLE`.
- Retryable tasks are resubmitted.
- Final task status becomes `SUCCESS` if it completes.

## Simulate JVM shutdown

```bash
curl -X POST http://localhost:8080/async-tasks/simulate-system-exit
```

Expected effect:

- JVM starts shutdown sequence.
- Spring context closes.
- `ContextClosedEvent` / `@PreDestroy` attempts to persist task state.

## Important boundary cases

| Shutdown type | Can persist state during shutdown? | Notes |
|---|---:|---|
| Spring context close | Yes | Best local simulation. |
| `SIGTERM` | Usually yes | Common container/K8s graceful stop path. |
| `System.exit(0)` | Usually yes | JVM shutdown hooks run. |
| `kill -9` / power loss / container hard kill | No | Must rely on startup recovery: stale `RUNNING` -> `RETRYABLE`. |
| OOM / process crash | Not reliable | Must rely on persisted heartbeat / updated_at timeout. |

## Production recommendation

Use a DB-backed task table:

```sql
id, status, progress, retry_count, payload, error_message, created_at, updated_at
```

State machine:

```text
PENDING -> RUNNING -> SUCCESS
                 ├-> FAILED
                 └-> RETRYABLE -> RUNNING
```

Startup recovery rule:

```sql
UPDATE async_task_state
SET status = 'RETRYABLE'
WHERE status = 'RUNNING'
  AND updated_at < now() - recovery_timeout;
```

For single-machine tests, H2 file DB is enough. For real deployment, use MySQL/PostgreSQL/Redis/queue middleware.

## Why CompletableFuture is insufficient

`CompletableFuture.cancel(true)` does not interrupt the underlying computation. It only completes the future exceptionally with `CancellationException`. Therefore the running task code must cooperate by checking a shutdown flag and periodically persisting progress.
