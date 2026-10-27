package com.tutorslot.notification;

// Serialized straight to JSON as the HTTP request body -- Jackson writes `type` as its enum name
// (e.g. "BOOKING_CONFIRMED"), which happens to be exactly the plain-String shape the mock service's
// own IncomingNotification expects. The two sides still don't share a Java class, just the wire
// format, same as talking to any real external API.
public record NotificationRequest(
        String idempotencyKey,
        String recipient,
        NotificationType type,
        String message,
        Long appointmentId
) {
}
