package com.example.demo3;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AsyncTaskService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AsyncTaskService.class);

    private final Executor taskExecutor;
    private final TaskStateRepository repository;
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, CompletableFuture<Void>> futures = new ConcurrentHashMap<>();

    public AsyncTaskService(@Qualifier("taskExecutor") Executor taskExecutor, TaskStateRepository repository) {
        this.taskExecutor = taskExecutor;
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        int changed = repository.markAllRunningAsRetryable("application startup recovered stale RUNNING task");
        if (changed > 0) {
            log.info("Recovered {} stale RUNNING task(s) as RETRYABLE", changed);
        }

        List<TaskState> retryableTasks = repository.findRetryableTasks();
        for (TaskState task : retryableTasks) {
            retry(task.id());
        }
    }

    public String submit(String payload) {
        String taskId = UUID.randomUUID().toString();
        repository.createRunning(taskId, payload);
        runAsync(taskId, payload, 0);
        return taskId;
    }

    public void retry(String taskId) {
        repository.increaseRetryCount(taskId);
        repository.markRunning(taskId);
        runAsync(taskId, "retry", 0);
    }

    public List<TaskState> list() {
        return repository.findAll();
    }

    private void runAsync(String taskId, String payload, int startProgress) {
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> doWork(taskId, payload, startProgress), taskExecutor)
                .whenComplete((ignored, throwable) -> futures.remove(taskId));
        futures.put(taskId, future);
    }

    private void doWork(String taskId, String payload, int startProgress) {
        log.info("Task {} started, payload={}", taskId, payload);
        try {
            for (int progress = startProgress; progress <= 100; progress += 5) {
                if (shuttingDown.get()) {
                    repository.markRetryable(taskId, "application is shutting down");
                    log.info("Task {} marked RETRYABLE because application is shutting down", taskId);
                    return;
                }

                repository.updateProgress(taskId, progress);
                Thread.sleep(1_000L);
            }

            repository.markSuccess(taskId);
            log.info("Task {} success", taskId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            repository.markRetryable(taskId, "task thread interrupted");
        } catch (Exception e) {
            repository.markFailed(taskId, e.getMessage());
        }
    }

    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        if (shuttingDown.compareAndSet(false, true)) {
            int changed = repository.markAllRunningAsRetryable("ContextClosedEvent received");
            log.info("ContextClosedEvent: marked {} RUNNING task(s) as RETRYABLE", changed);
        }
    }

    @PreDestroy
    public void onPreDestroy() {
        if (shuttingDown.compareAndSet(false, true)) {
            int changed = repository.markAllRunningAsRetryable("PreDestroy received");
            log.info("PreDestroy: marked {} RUNNING task(s) as RETRYABLE", changed);
        }
    }
}
