package com.tutorslot.notification;

// What the gateway hands back, success or failure -- never an exception. On success, status is
// whatever the service reported (ACCEPTED or DUPLICATE). On failure, errorMessage says why the
// last attempt didn't work.
public record NotificationResult(
        boolean success,
        String status,
        String errorMessage
) {
    public static NotificationResult success(String status) {
        return new NotificationResult(true, status, null);
    }

    public static NotificationResult failure(String errorMessage) {
        return new NotificationResult(false, null, errorMessage);
    }
}
