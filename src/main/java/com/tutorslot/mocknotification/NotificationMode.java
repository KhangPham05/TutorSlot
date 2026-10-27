package com.tutorslot.mocknotification;

// Lets us simulate the external service being healthy, down, or just slow, so M3's failure
// handling (timeouts, retry, the resend job) can actually be tested and demoed, not just argued
// about.
public enum NotificationMode {
    UP,
    DOWN,
    SLOW
}
