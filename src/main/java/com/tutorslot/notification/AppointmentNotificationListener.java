package com.tutorslot.notification;

import com.tutorslot.model.Appointment;
import com.tutorslot.model.AvailabilitySlot;
import com.tutorslot.model.Provider;
import com.tutorslot.model.Subject;
import com.tutorslot.model.User;
import com.tutorslot.repository.AppointmentRepository;
import com.tutorslot.repository.AvailabilitySlotRepository;
import com.tutorslot.repository.PendingNotificationRepository;
import com.tutorslot.repository.ProviderRepository;
import com.tutorslot.repository.SubjectRepository;
import com.tutorslot.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;

// Runs only after the booking/cancel transaction actually commits (AFTER_COMMIT) -- if it rolled
// back (a 409, or a lock retry BookingFacade is still working through), the event was never
// published for a real commit, so Spring never calls this at all: no notification for a booking
// that didn't happen. @Async means it runs on notificationExecutor, off the request thread, so a
// slow or down notification service never makes a student wait on their own booking page -- and
// crucially, the remote call happens outside the database transaction entirely, so it can never
// hold the slot's row lock.
@Component
public class AppointmentNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(AppointmentNotificationListener.class);
    private static final DateTimeFormatter MESSAGE_TIME_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a");

    private final NotificationGateway gateway;
    private final PendingNotificationRepository pendingNotificationRepository;
    private final AppointmentRepository appointmentRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final ProviderRepository providerRepository;
    private final UserRepository userRepository;
    private final SubjectRepository subjectRepository;
    private final MeterRegistry meterRegistry;

    public AppointmentNotificationListener(NotificationGateway gateway,
                                            PendingNotificationRepository pendingNotificationRepository,
                                            AppointmentRepository appointmentRepository,
                                            AvailabilitySlotRepository availabilitySlotRepository,
                                            ProviderRepository providerRepository, UserRepository userRepository,
                                            SubjectRepository subjectRepository, MeterRegistry meterRegistry) {
        this.gateway = gateway;
        this.pendingNotificationRepository = pendingNotificationRepository;
        this.appointmentRepository = appointmentRepository;
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.providerRepository = providerRepository;
        this.userRepository = userRepository;
        this.subjectRepository = subjectRepository;
        this.meterRegistry = meterRegistry;
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBooked(AppointmentBookedEvent event) {
        notify(event.appointmentId(), "BOOKED", NotificationType.BOOKING_CONFIRMED, "confirmed");
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCancelled(AppointmentCancelledEvent event) {
        notify(event.appointmentId(), "CANCELLED", NotificationType.BOOKING_CANCELLED, "cancelled");
    }

    private void notify(Long appointmentId, String keyPrefix, NotificationType type, String verb) {
        // The transaction already committed by the time this runs, so every row here is final.
        Appointment appointment = appointmentRepository.findById(appointmentId).orElseThrow();
        AvailabilitySlot slot = availabilitySlotRepository.findById(appointment.slotId()).orElseThrow();
        Provider provider = providerRepository.findById(slot.providerId()).orElseThrow();
        User tutor = userRepository.findById(provider.userId()).orElseThrow();
        Subject subject = subjectRepository.findById(slot.serviceId()).orElseThrow();
        User customer = userRepository.findById(appointment.customerId()).orElseThrow();

        String message = "Your %s session with %s on %s is %s.".formatted(
                subject.name(), tutor.fullName(), slot.startTime().format(MESSAGE_TIME_FORMAT), verb);
        String idempotencyKey = keyPrefix + "-" + appointmentId;
        NotificationRequest request = new NotificationRequest(idempotencyKey, customer.email(), type, message, appointmentId);

        NotificationResult result = gateway.send(request);
        if (result.success()) {
            log.info("Notification sent: key={} attempt={}", idempotencyKey, result.attempts());
            meterRegistry.counter("tutorslot.notifications", "result", "sent").increment();
        } else {
            log.warn("Notification service unavailable, queued for resend: key={}", idempotencyKey);
            pendingNotificationRepository.save(idempotencyKey, request.recipient(), type.name(), message, appointmentId);
            meterRegistry.counter("tutorslot.notifications", "result", "fallback").increment();
        }
    }
}
