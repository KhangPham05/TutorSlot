package com.tutorslot.notification;

import com.tutorslot.model.PendingNotification;
import com.tutorslot.repository.PendingNotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationResendJobTest {

    @Mock
    private NotificationGateway gateway;
    @Mock
    private PendingNotificationRepository pendingNotificationRepository;

    @InjectMocks
    private NotificationResendJob resendJob;

    private static final Long PENDING_ID = 1L;

    private PendingNotification pending(int attempts) {
        return new PendingNotification(PENDING_ID, "BOOKED-7", "dan.student@sjsu.edu",
                "BOOKING_CONFIRMED", "Your session is confirmed.", 7L, "PENDING", attempts, null,
                LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    void marksRowSentOnSuccess() {
        when(pendingNotificationRepository.findDue(50)).thenReturn(List.of(pending(0)));
        when(gateway.send(any())).thenReturn(NotificationResult.success("ACCEPTED", 1));

        resendJob.resendPending();

        verify(pendingNotificationRepository).markSent(PENDING_ID);
        verify(pendingNotificationRepository, never()).recordFailure(eq(PENDING_ID), any());
        verify(pendingNotificationRepository, never()).markFailed(PENDING_ID);
    }

    @Test
    void incrementsAttemptsOnFailureWithoutMarkingFailedYet() {
        // Already failed twice (attempts=2); this will be the 3rd -- below the 5-attempt limit.
        when(pendingNotificationRepository.findDue(50)).thenReturn(List.of(pending(2)));
        when(gateway.send(any())).thenReturn(NotificationResult.failure("503", 3));

        resendJob.resendPending();

        verify(pendingNotificationRepository).recordFailure(eq(PENDING_ID), any());
        verify(pendingNotificationRepository, never()).markFailed(PENDING_ID);
    }

    @Test
    void marksFailedAfterFiveTotalAttempts() {
        // Already failed 4 times; this failure is the 5th -- hits the limit.
        when(pendingNotificationRepository.findDue(50)).thenReturn(List.of(pending(4)));
        when(gateway.send(any())).thenReturn(NotificationResult.failure("503", 3));

        resendJob.resendPending();

        verify(pendingNotificationRepository).recordFailure(eq(PENDING_ID), any());
        verify(pendingNotificationRepository).markFailed(PENDING_ID);
    }
}
