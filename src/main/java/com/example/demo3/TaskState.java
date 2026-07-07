package com.example.demo3;

import java.time.LocalDateTime;

public record TaskState(
        String id,
        String status,
        int progress,
        int retryCount,
        String payload,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
