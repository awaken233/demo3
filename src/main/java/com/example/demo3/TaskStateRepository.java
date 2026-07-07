package com.example.demo3;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class TaskStateRepository {

    private final JdbcTemplate jdbcTemplate;

    public TaskStateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void createRunning(String taskId, String payload) {
        jdbcTemplate.update("""
                INSERT INTO async_task_state(id, status, progress, retry_count, payload, error_message, created_at, updated_at)
                VALUES (?, 'RUNNING', 0, 0, ?, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, taskId, payload);
    }

    public void markRunning(String taskId) {
        jdbcTemplate.update("""
                UPDATE async_task_state
                SET status = 'RUNNING', updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, taskId);
    }

    public void updateProgress(String taskId, int progress) {
        jdbcTemplate.update("""
                UPDATE async_task_state
                SET progress = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, progress, taskId);
    }

    public void markSuccess(String taskId) {
        jdbcTemplate.update("""
                UPDATE async_task_state
                SET status = 'SUCCESS', progress = 100, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, taskId);
    }

    public void markRetryable(String taskId, String reason) {
        jdbcTemplate.update("""
                UPDATE async_task_state
                SET status = 'RETRYABLE', error_message = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'RUNNING'
                """, reason, taskId);
    }

    public int markAllRunningAsRetryable(String reason) {
        return jdbcTemplate.update("""
                UPDATE async_task_state
                SET status = 'RETRYABLE', error_message = ?, updated_at = CURRENT_TIMESTAMP
                WHERE status = 'RUNNING'
                """, reason);
    }

    public void markFailed(String taskId, String errorMessage) {
        jdbcTemplate.update("""
                UPDATE async_task_state
                SET status = 'FAILED', error_message = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, errorMessage, taskId);
    }

    public List<TaskState> findRetryableTasks() {
        return jdbcTemplate.query("""
                SELECT id, status, progress, retry_count, payload, error_message, created_at, updated_at
                FROM async_task_state
                WHERE status = 'RETRYABLE'
                ORDER BY created_at
                """, (rs, rowNum) -> new TaskState(
                rs.getString("id"),
                rs.getString("status"),
                rs.getInt("progress"),
                rs.getInt("retry_count"),
                rs.getString("payload"),
                rs.getString("error_message"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class)
        ));
    }

    public List<TaskState> findAll() {
        return jdbcTemplate.query("""
                SELECT id, status, progress, retry_count, payload, error_message, created_at, updated_at
                FROM async_task_state
                ORDER BY created_at DESC
                """, (rs, rowNum) -> new TaskState(
                rs.getString("id"),
                rs.getString("status"),
                rs.getInt("progress"),
                rs.getInt("retry_count"),
                rs.getString("payload"),
                rs.getString("error_message"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class)
        ));
    }

    public void increaseRetryCount(String taskId) {
        jdbcTemplate.update("""
                UPDATE async_task_state
                SET retry_count = retry_count + 1, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, taskId);
    }
}
