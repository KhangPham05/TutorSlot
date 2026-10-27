package com.tutorslot.mocknotification;

// This is the mock service's OWN copy of the request shape -- it does not share a Java class
// with the real client side (com.tutorslot.notification, added in Step 3). The two sides only
// agree on the JSON they exchange, exactly like talking to a real separate company's API: we
// couldn't import their classes even if we wanted to. type is kept as a plain String here, not
// our own enum, for the same reason -- a real external service doesn't know our Java types.
public record IncomingNotification(
        String idempotencyKey,
        String recipient,
        String type,
        String message,
        Long appointmentId
) {
}
