package com.tutorslot.model;

import java.time.LocalDateTime;

public record PendingNotification(
        Long pendingId,
        String idempotencyKey,
        String recipient,
        String type,
        String message,
        Long appointmentId,
        String status,
        int attempts,
        String lastError,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
