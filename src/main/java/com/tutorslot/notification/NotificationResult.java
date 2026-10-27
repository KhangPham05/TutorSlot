package com.tutorslot.notification;

// What the gateway hands back, success or failure -- never an exception. On success, status is
// whatever the service reported (ACCEPTED or DUPLICATE). On failure, errorMessage says why the
// last attempt didn't work. attempts is how many tries it took (or used up), for the caller's
// "Notification sent: key=... attempt=N" log line.
public record NotificationResult(
        boolean success,
        String status,
        String errorMessage,
        int attempts
) {
    public static NotificationResult success(String status, int attempts) {
        return new NotificationResult(true, status, null, attempts);
    }

    public static NotificationResult failure(String errorMessage, int attempts) {
        return new NotificationResult(false, null, errorMessage, attempts);
    }
}
