package com.tutorslot.notification;

/**
 * The client side of the distribution boundary to the (mocked) external notification service.
 * <p>
 * One coarse-grained method on purpose: a single {@code send} call carries everything the remote
 * service needs to deliver one message. The alternative is a chatty interface -- separate
 * {@code createMessage()}, {@code setRecipient()}, {@code setBody()}, {@code send()} calls -- which
 * turns one logical action into four network round trips. Each remote call can be slow or fail, so
 * fewer and larger calls mean less latency, fewer places for a failure to happen, and exactly one
 * unit of work to retry idempotently (see {@link NotificationRequest#idempotencyKey()}).
 * <p>
 * Milestone 4 reuses this same {@code send()} for AI-generated reminders, just with a new
 * {@link NotificationType} value -- the interface doesn't need to change for that.
 */
public interface NotificationGateway {

    NotificationResult send(NotificationRequest request);
}
