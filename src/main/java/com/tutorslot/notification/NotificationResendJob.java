package com.tutorslot.notification;

import com.tutorslot.model.PendingNotification;
import com.tutorslot.repository.PendingNotificationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

// Delivers whatever HttpNotificationGateway couldn't, periodically, using each row's own
// idempotency key -- so even if an earlier "failed" attempt actually reached the mock service
// (e.g. the response was lost after it processed the request), resending is still safe: the mock
// just reports DUPLICATE instead of storing it twice.
@Component
public class NotificationResendJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationResendJob.class);
    private static final int BATCH_LIMIT = 50;
    private static final int MAX_TOTAL_ATTEMPTS = 5;

    private final NotificationGateway gateway;
    private final PendingNotificationRepository pendingNotificationRepository;
    private final MeterRegistry meterRegistry;

    public NotificationResendJob(NotificationGateway gateway, PendingNotificationRepository pendingNotificationRepository,
                                  MeterRegistry meterRegistry) {
        this.gateway = gateway;
        this.pendingNotificationRepository = pendingNotificationRepository;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${notification.resend-interval-ms:30000}")
    public void resendPending() {
        List<PendingNotification> due = pendingNotificationRepository.findDue(BATCH_LIMIT);
        for (PendingNotification pending : due) {
            resend(pending);
        }
    }

    private void resend(PendingNotification pending) {
        NotificationRequest request = new NotificationRequest(
                pending.idempotencyKey(), pending.recipient(),
                NotificationType.valueOf(pending.type()), pending.message(), pending.appointmentId());

        NotificationResult result = gateway.send(request);

        if (result.success()) {
            pendingNotificationRepository.markSent(pending.pendingId());
            log.info("Notification sent: key={} attempt={}", pending.idempotencyKey(), result.attempts());
            meterRegistry.counter("tutorslot.notifications", "result", "sent").increment();
            return;
        }

        // attempts here counts resend cycles, not the gateway's own internal retries within one
        // cycle -- this is the (pending.attempts() + 1)th time we've tried to deliver this row.
        pendingNotificationRepository.recordFailure(pending.pendingId(), result.errorMessage());
        int totalAttempts = pending.attempts() + 1;
        if (totalAttempts >= MAX_TOTAL_ATTEMPTS) {
            pendingNotificationRepository.markFailed(pending.pendingId());
            log.error("Notification permanently failed after {} attempts: key={}", totalAttempts, pending.idempotencyKey());
            meterRegistry.counter("tutorslot.notifications", "result", "failed").increment();
        }
    }
}
