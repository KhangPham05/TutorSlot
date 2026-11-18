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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// A real ChatClient around a mocked ChatModel, so no test calls Gemini.
@ExtendWith(MockitoExtension.class)
class ReminderAgentTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 18, 10, 0);
    private static final Appointment APPOINTMENT = new Appointment(12L, 7L, 5L, "BOOKED",
            "Please bring past exams", START.minusDays(2), null);
    private static final String TEMPLATE =
            "Reminder: your Calculus I session with Alice Nguyen is on Wednesday, November 18 at 10:00 AM.";

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private AvailabilitySlotService availabilitySlotService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationGateway gateway;
    @Mock
    private PendingNotificationRepository pendingNotificationRepository;
    @Mock
    private ChatModel chatModel;

    private ReminderAgent agent(boolean aiEnabled, int maxPerRun) {
        lenient().when(chatModel.getDefaultOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        AiAvailability availability = new AiAvailability(aiEnabled ? "test-key" : AiAvailability.PLACEHOLDER_KEY);
        return new ReminderAgent(appointmentRepository, availabilitySlotService, userRepository, gateway,
                pendingNotificationRepository, availability, ChatClient.builder(chatModel), 24, maxPerRun);
    }

    private void oneAppointmentDue() {
        when(appointmentRepository.findBookedNeedingReminder(any(), any(), anyInt())).thenReturn(List.of(APPOINTMENT));
        when(availabilitySlotService.findSlotSummary(7L))
                .thenReturn(new SlotDto(7L, "Alice Nguyen", "Calculus I", START, START.plusHours(1)));
        when(userRepository.findById(5L)).thenReturn(Optional.of(
                new User(5L, "dan.student@sjsu.edu", "hash", "Dan Kim", "CUSTOMER", START.minusDays(30))));
    }

    private NotificationRequest sentRequest() {
        ArgumentCaptor<NotificationRequest> request = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(gateway).send(request.capture());
        return request.getValue();
    }

    @Test
    void sendsTheAiReminderThroughTheGatewayWithKeyReminderIdAndMarksTheAppointment() {
        ReminderAgent agent = agent(true, 5);
        oneAppointmentDue();
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage("Hi Dan! Your Calculus I session with Alice Nguyen is tomorrow.")))));
        when(gateway.send(any())).thenReturn(NotificationResult.success("ACCEPTED", 1));

        int handled = agent.sendDueReminders();

        assertThat(handled).isEqualTo(1);
        NotificationRequest request = sentRequest();
        assertThat(request.idempotencyKey()).isEqualTo("REMINDER-12");
        assertThat(request.type()).isEqualTo(NotificationType.APPOINTMENT_REMINDER);
        assertThat(request.recipient()).isEqualTo("dan.student@sjsu.edu");
        assertThat(request.message()).isEqualTo("Hi Dan! Your Calculus I session with Alice Nguyen is tomorrow.");
        verify(appointmentRepository).markReminderSent(12L);
        verify(pendingNotificationRepository, never()).save(any(), any(), any(), any(), any());
    }

    @Test
    void onlyTheFirstNameSubjectTutorAndTimeAreSentToGemini() {
        ReminderAgent agent = agent(true, 5);
        oneAppointmentDue();
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Hi!")))));
        when(gateway.send(any())).thenReturn(NotificationResult.success("ACCEPTED", 1));

        agent.sendDueReminders();

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getContents())
                .contains("Dan", "Calculus I", "Alice Nguyen", "Wednesday, November 18 at 10:00 AM")
                .doesNotContain("Kim", "dan.student@sjsu.edu", "past exams");
    }

    @Test
    void fallsBackToTheTemplateWhenTheModelCallThrows() {
        ReminderAgent agent = agent(true, 5);
        oneAppointmentDue();
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("503 high demand"));
        when(gateway.send(any())).thenReturn(NotificationResult.success("ACCEPTED", 1));

        agent.sendDueReminders();

        assertThat(sentRequest().message()).isEqualTo(TEMPLATE);
        verify(appointmentRepository).markReminderSent(12L);
    }

    @Test
    void withAiDisabledItSendsTheTemplateAndNeverCallsGemini() {
        ReminderAgent agent = agent(false, 5);
        oneAppointmentDue();
        when(gateway.send(any())).thenReturn(NotificationResult.success("ACCEPTED", 1));

        agent.sendDueReminders();

        assertThat(sentRequest().message()).isEqualTo(TEMPLATE);
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void aFailedSendIsQueuedForResendAndStillMarked() {
        ReminderAgent agent = agent(false, 5);
        oneAppointmentDue();
        when(gateway.send(any())).thenReturn(NotificationResult.failure("503", 3));

        agent.sendDueReminders();

        verify(pendingNotificationRepository).save("REMINDER-12", "dan.student@sjsu.edu", "APPOINTMENT_REMINDER",
                TEMPLATE, 12L);
        // Marked anyway: the outbox now owns delivery, and the next run must not pay for it again.
        verify(appointmentRepository).markReminderSent(12L);
    }

    @Test
    void respectsThePerRunCapAndTheWindow() {
        ReminderAgent agent = agent(false, 2);
        when(appointmentRepository.findBookedNeedingReminder(any(), any(), eq(2))).thenReturn(List.of());

        int handled = agent.sendDueReminders();

        assertThat(handled).isZero();
        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        // The cap goes into the query's LIMIT, so a run never loads (or sends) more than 2.
        verify(appointmentRepository).findBookedNeedingReminder(from.capture(), to.capture(), eq(2));
        assertThat(Duration.between(from.getValue(), to.getValue())).isEqualTo(Duration.ofHours(24));
        verify(gateway, never()).send(any());
    }
}
