package com.tutorslot.controller.api;

import com.tutorslot.ai.ReminderAgent;
import com.tutorslot.dto.CreateSlotRequest;
import com.tutorslot.dto.MyAppointmentsDto;
import com.tutorslot.dto.ProviderDashboardDto;
import com.tutorslot.model.Provider;
import com.tutorslot.service.AppointmentService;
import com.tutorslot.service.CurrentUserService;
import com.tutorslot.service.ProviderDashboardService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// A tutor's dashboard for the Next.js pages: the same service calls as ProviderController.
// /api/provider/** is PROVIDER-only (see ApiSecurityConfig), and the tutor is always the one in
// the token; the services check that a slot or subject really belongs to them.
@RestController
@RequestMapping("/api/provider")
public class ProviderApiController {

    private final ProviderDashboardService providerDashboardService;
    private final AppointmentService appointmentService;
    private final CurrentUserService currentUserService;
    private final ReminderAgent reminderAgent;

    public ProviderApiController(ProviderDashboardService providerDashboardService,
                                 AppointmentService appointmentService, CurrentUserService currentUserService,
                                 ReminderAgent reminderAgent) {
        this.providerDashboardService = providerDashboardService;
        this.appointmentService = appointmentService;
        this.currentUserService = currentUserService;
        this.reminderAgent = reminderAgent;
    }

    @GetMapping("/dashboard")
    public ProviderDashboardDto dashboard(Authentication authentication) {
        return providerDashboardService.getDashboard(tutor(authentication).providerId());
    }

    // Same validation as the web form (CreateSlotRequest): a past start time is a 400, a clash
    // with one of the tutor's own slots a 409.
    @PostMapping("/slots")
    public ResponseEntity<Map<String, Long>> createSlot(@Valid @RequestBody CreateSlotRequest request,
                                                        Authentication authentication) {
        Long slotId = providerDashboardService.createSlot(
                tutor(authentication).providerId(), request.serviceId(), request.startDateTime());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("slotId", slotId));
    }

    @PostMapping("/slots/{id}/delete")
    public ResponseEntity<Void> deleteSlot(@PathVariable("id") Long slotId, Authentication authentication) {
        providerDashboardService.removeSlot(slotId, tutor(authentication).providerId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/appointments")
    public MyAppointmentsDto appointments(Authentication authentication) {
        return appointmentService.getProviderAppointments(tutor(authentication).providerId());
    }

    // The "Send reminders now" button: the same method the 15-minute schedule calls.
    @PostMapping("/reminders/run")
    public Map<String, Integer> runReminders() {
        return Map.of("sent", reminderAgent.sendDueReminders());
    }

    private Provider tutor(Authentication authentication) {
        return currentUserService.requireProviderByEmail(authentication.getName());
    }
}
