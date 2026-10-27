package com.tutorslot.mocknotification;

import java.time.Instant;

// What the mock service keeps for a notification it accepted -- the incoming fields plus the id
// and timestamp the mock itself assigns, same as a real service would.
public record StoredNotification(
        Long notificationId,
        String idempotencyKey,
        String recipient,
        String type,
        String message,
        Long appointmentId,
        Instant receivedAt
) {
}
