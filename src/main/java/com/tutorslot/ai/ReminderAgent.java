package com.tutorslot.ai;

import com.tutorslot.dto.SlotDto;
import com.tutorslot.model.Appointment;
import com.tutorslot.model.User;
import com.tutorslot.notification.NotificationGateway;
import com.tutorslot.notification.NotificationRequest;
import com.tutorslot.notification.NotificationResult;
import com.tutorslot.notification.NotificationType;
import com.tutorslot.repository.AppointmentRepository;
import com.tutorslot.repository.PendingNotificationRepository;
import com.tutorslot.repository.UserRepository;
import com.tutorslot.service.AvailabilitySlotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

// The proactive agent: on a schedule, it finds sessions starting soon, asks Gemini for a short
// personal reminder, and sends it through the same notification gateway as booking confirmations.
@Component
public class ReminderAgent {

    private static final Logger log = LoggerFactory.getLogger(ReminderAgent.class);

    private static final DateTimeFormatter SESSION_TIME = DateTimeFormatter.ofPattern("EEEE, MMMM d 'at' h:mm a", Locale.US);

    // Only the first name, subject, tutor, and time go to Gemini: never the email or the notes.
    private static final String PROMPT = """
            Write a short, friendly reminder for a student named %s about their tutoring session: \
            %s with %s on %s. Use 2 to 3 sentences of plain text, with no subject line and no \
            signature. Keep the subject, tutor, date, and time exactly as given.""";

    private final AppointmentRepository appointmentRepository;
    private final AvailabilitySlotService availabilitySlotService;
    private final UserRepository userRepository;
    private final NotificationGateway gateway;
    private final PendingNotificationRepository pendingNotificationRepository;
    private final AiAvailability aiAvailability;
    private final ChatClient chatClient;
    private final int windowHours;
    private final int maxPerRun;

    public ReminderAgent(AppointmentRepository appointmentRepository, AvailabilitySlotService availabilitySlotService,
                         UserRepository userRepository, NotificationGateway gateway,
                         PendingNotificationRepository pendingNotificationRepository, AiAvailability aiAvailability,
                         ChatClient.Builder chatClientBuilder,
                         @Value("${reminders.window-hours:24}") int windowHours,
                         @Value("${reminders.max-per-run:5}") int maxPerRun) {
        this.appointmentRepository = appointmentRepository;
        this.availabilitySlotService = availabilitySlotService;
        this.userRepository = userRepository;
        this.gateway = gateway;
        this.pendingNotificationRepository = pendingNotificationRepository;
        this.aiAvailability = aiAvailability;
        this.chatClient = chatClientBuilder.build();
        this.windowHours = windowHours;
        this.maxPerRun = maxPerRun;
    }

    // Runs every reminders.interval-ms (15 minutes by default). The initial delay matters: without
    // it Spring runs a fixed-delay job once right at startup. The dashboard's "Send reminders now"
    // button calls this same method; synchronized makes a click and a scheduled run take turns
    // instead of both picking up the same appointments. Returns how many reminders it handled.
    @Scheduled(initialDelayString = "${reminders.interval-ms:900000}", fixedDelayString = "${reminders.interval-ms:900000}")
    public synchronized int sendDueReminders() {
        LocalDateTime now = LocalDateTime.now();
        // The cap protects the free Gemini quota: at most maxPerRun AI calls per run. Anything left
        // over is picked up by the next run.
        List<Appointment> due = appointmentRepository.findBookedNeedingReminder(now, now.plusHours(windowHours), maxPerRun);
        for (Appointment appointment : due) {
            remind(appointment);
        }
        return due.size();
    }

    private void remind(Appointment appointment) {
        Long appointmentId = appointment.appointmentId();
        SlotDto slot = availabilitySlotService.findSlotSummary(appointment.slotId());
        User student = userRepository.findById(appointment.customerId()).orElseThrow();
        String time = slot.startTime().format(SESSION_TIME);

        String message = aiReminder(appointmentId, student, slot, time);
        String source = "ai";
        if (message == null) {
            // The fallback: when AI is off or Gemini fails, a plain template still gets sent.
            message = "Reminder: your %s session with %s is on %s.".formatted(slot.subjectName(), slot.tutorName(), time);
            source = "template";
        }

        // The idempotency key: one reminder per appointment, ever. If the same reminder is sent
        // again (a crash before markReminderSent below, or a resend from the pending table), the
        // notification service sees the same key and answers DUPLICATE instead of emailing twice.
        String key = "REMINDER-" + appointmentId;
        NotificationRequest request = new NotificationRequest(key, student.email(),
                NotificationType.APPOINTMENT_REMINDER, message, appointmentId);
        NotificationResult result = gateway.send(request);
        if (result.success()) {
            log.info("Reminder sent: appointment={} source={}", appointmentId, source);
        } else {
            // Same outbox as booking confirmations: NotificationResendJob delivers it later.
            pendingNotificationRepository.save(key, student.email(), NotificationType.APPOINTMENT_REMINDER.name(),
                    message, appointmentId);
            log.warn("Reminder queued for resend: appointment={} source={}", appointmentId, source);
        }
        // Marked either way (sent, or safely queued), so the next run doesn't spend another Gemini
        // call on it.
        appointmentRepository.markReminderSent(appointmentId);
    }

    // The reminder text from Gemini, or null when AI is off or the call fails.
    private String aiReminder(Long appointmentId, User student, SlotDto slot, String time) {
        if (!aiAvailability.isEnabled()) {
            return null;
        }
        try {
            String firstName = student.fullName().split(" ")[0];
            String text = GeminiText.of(chatClient.prompt()
                    .user(PROMPT.formatted(firstName, slot.subjectName(), slot.tutorName(), time))
                    .call()
                    .chatResponse());
            return text == null ? null : text.strip();
        } catch (RuntimeException e) {
            log.warn("Reminder text from Gemini failed, using the template: appointment={} error={}",
                    appointmentId, GeminiText.describeError(e));
            return null;
        }
    }
}
