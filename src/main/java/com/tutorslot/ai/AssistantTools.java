package com.tutorslot.ai;

import com.tutorslot.dto.AppointmentSummaryDto;
import com.tutorslot.dto.MyAppointmentsDto;
import com.tutorslot.dto.SlotDto;
import com.tutorslot.dto.SlotsPageDto;
import com.tutorslot.exception.ForbiddenException;
import com.tutorslot.exception.NotFoundException;
import com.tutorslot.exception.SlotConflictException;
import com.tutorslot.service.AppointmentService;
import com.tutorslot.service.AvailabilitySlotService;
import com.tutorslot.service.BookingFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

// The assistant's tools: each one is a thin wrapper around an existing service method, so the AI
// path gets exactly the same rules as the web pages -- the row lock, the unique index, the owner
// check, and the after-commit notification all still apply.
//
// The tool context is how we pass trusted data to the tools without going through the model:
// AssistantService fills it with the logged-in student's id and the current turn number, Spring
// AI hands it to any tool method with a ToolContext parameter, and the model never sees it. That
// is why no tool has a user id or email parameter -- the model can't book for someone else even
// if a message tries to trick it into that.
@Component
public class AssistantTools {

    private static final Logger log = LoggerFactory.getLogger(AssistantTools.class);

    static final String CUSTOMER_ID = "customerId";
    static final String TURN = "turn";

    private static final int MAX_PAGES = 3;
    private static final DateTimeFormatter DAY_AND_TIME = DateTimeFormatter.ofPattern("EEE MMM d, yyyy h:mm a", Locale.US);
    private static final DateTimeFormatter TIME_ONLY = DateTimeFormatter.ofPattern("h:mm a", Locale.US);

    private final AvailabilitySlotService availabilitySlotService;
    private final AppointmentService appointmentService;
    private final BookingFacade bookingFacade;
    private final PendingActionStore pendingActionStore;
    private final PolicySearchService policySearchService;

    public AssistantTools(AvailabilitySlotService availabilitySlotService, AppointmentService appointmentService,
                          BookingFacade bookingFacade, PendingActionStore pendingActionStore,
                          PolicySearchService policySearchService) {
        this.availabilitySlotService = availabilitySlotService;
        this.appointmentService = appointmentService;
        this.bookingFacade = bookingFacade;
        this.pendingActionStore = pendingActionStore;
        this.policySearchService = policySearchService;
    }

    // What AssistantService passes as the tool context on every chat request.
    public static Map<String, Object> toolContext(Long customerId, long turn) {
        return Map.of(CUSTOMER_ID, customerId, TURN, turn);
    }

    @Tool(description = "Find open tutoring slots, earliest first. Every filter is optional: "
            + "leave out any the student did not mention.")
    public String findAvailableSlots(
            @ToolParam(required = false, description = "Part of the tutor's name, for example Alice") String tutorName,
            @ToolParam(required = false, description = "Part of the subject name, for example Calculus") String subjectName,
            @ToolParam(required = false, description = "Earliest date to include, as yyyy-MM-dd") String fromDate,
            @ToolParam(required = false, description = "Latest date to include, as yyyy-MM-dd") String toDate,
            ToolContext toolContext) {
        Long customerId = customerId(toolContext);
        log.info("AI tool call: tool=findAvailableSlots user={}", customerId);
        return safely("findAvailableSlots", customerId, () -> {
            LocalDate from;
            LocalDate to;
            try {
                from = parseDate(fromDate);
                to = parseDate(toDate);
            } catch (DateTimeParseException e) {
                return "FAILED: dates must look like 2026-11-17";
            }

            List<SlotDto> matches = openSlots(tutorName, subjectName, from, to);
            if (matches.isEmpty()) {
                return "No open slots match.";
            }
            return matches.stream()
                    .map(AssistantTools::describeSlot)
                    .collect(Collectors.joining("\n"));
        });
    }

    @Tool(description = "List the student's own appointments: upcoming ones (which can be cancelled) and history.")
    public String listMyAppointments(ToolContext toolContext) {
        Long customerId = customerId(toolContext);
        log.info("AI tool call: tool=listMyAppointments user={}", customerId);
        return safely("listMyAppointments", customerId, () -> {
            MyAppointmentsDto mine = appointmentService.getMyAppointments(customerId);
            // Notes are left out on purpose: they're the student's private text and the model
            // doesn't need them to book or cancel.
            String upcoming = mine.upcoming().isEmpty() ? "(none)"
                    : mine.upcoming().stream().map(AssistantTools::describeAppointment).collect(Collectors.joining("\n"));
            String history = mine.history().isEmpty() ? "(none)"
                    : mine.history().stream().map(a -> describeAppointment(a) + " (" + a.status() + ")")
                    .collect(Collectors.joining("\n"));
            return "Upcoming:\n" + upcoming + "\nHistory:\n" + history;
        });
    }

    @Tool(description = "Prepare a booking for one slot WITHOUT booking it. Returns the session details: show them "
            + "to the student and ask \"Shall I confirm?\". Nothing is booked until confirmPendingAction.")
    public String proposeBooking(
            @ToolParam(description = "The slot id, from findAvailableSlots") Long slotId,
            ToolContext toolContext) {
        Long customerId = customerId(toolContext);
        log.info("AI tool call: tool=proposeBooking user={} slot={}", customerId, slotId);
        return safely("proposeBooking", customerId, () -> {
            SlotDto slot = availabilitySlotService.findSlotSummary(slotId);
            pendingActionStore.save(customerId, new PendingAction(PendingAction.Type.BOOK, slotId, turn(toolContext)));
            return "PROPOSED booking: " + describeSlot(slot) + ". Show this to the student and ask: Shall I confirm?";
        });
    }

    @Tool(description = "Prepare the cancellation of one of the student's upcoming appointments WITHOUT cancelling "
            + "it. Show the details to the student and ask \"Shall I confirm?\". Nothing is cancelled until "
            + "confirmPendingAction.")
    public String proposeCancellation(
            @ToolParam(description = "The appointment id, from listMyAppointments") Long appointmentId,
            ToolContext toolContext) {
        Long customerId = customerId(toolContext);
        log.info("AI tool call: tool=proposeCancellation user={} appointment={}", customerId, appointmentId);
        return safely("proposeCancellation", customerId, () -> {
            // Only the student's OWN upcoming appointments can be proposed, so an id from someone
            // else's booking (or an already finished one) is turned away right here.
            Optional<AppointmentSummaryDto> appointment = appointmentService.getMyAppointments(customerId).upcoming()
                    .stream()
                    .filter(a -> a.appointmentId().equals(appointmentId))
                    .findFirst();
            if (appointment.isEmpty()) {
                return "FAILED: appointment " + appointmentId + " is not one of the student's upcoming appointments";
            }
            pendingActionStore.save(customerId, new PendingAction(PendingAction.Type.CANCEL, appointmentId, turn(toolContext)));
            return "PROPOSED cancellation: " + describeAppointment(appointment.get())
                    + ". Show this to the student and ask: Shall I confirm?";
        });
    }

    @Tool(description = "Carry out the booking or cancellation that was proposed earlier. Call this ONLY after "
            + "the student has said yes in a new message.")
    public String confirmPendingAction(ToolContext toolContext) {
        Long customerId = customerId(toolContext);
        long currentTurn = turn(toolContext);
        Optional<PendingAction> pending = pendingActionStore.find(customerId);
        log.info("AI tool call: tool=confirmPendingAction user={} action={}", customerId,
                pending.map(p -> p.type() + " " + p.targetId()).orElse("none"));

        if (pending.isEmpty()) {
            return "FAILED: there is nothing to confirm. Propose a booking or cancellation first.";
        }
        PendingAction action = pending.get();

        // The confirm rule, enforced in code instead of trusted to the prompt: the proposal must
        // come from an EARLIER turn than this one. So the student has seen the summary and sent
        // at least one new message since -- even if the model tries to propose and confirm in a
        // single step, nothing is booked on the turn it was proposed. The proposal is kept, so
        // the student's "yes" in the next message still works.
        if (action.turn() >= currentTurn) {
            return "FAILED: ask the student to confirm first";
        }

        return safely("confirmPendingAction", customerId, () -> {
            // Used up from here on, whether the commit below succeeds or fails.
            pendingActionStore.clear(customerId);
            if (action.type() == PendingAction.Type.BOOK) {
                // Same path as the Book button: BookingFacade's retry loop, then BookingService's
                // locked, transactional check. Notes stay null; the assistant never collects any.
                try {
                    Long appointmentId = bookingFacade.book(action.targetId(), customerId, null);
                    return "BOOKED: appointment " + appointmentId + " for slot " + action.targetId()
                            + ". A confirmation email is on its way.";
                } catch (SlotConflictException e) {
                    // Someone else booked it after it was proposed. The result lists the next open
                    // slots for the same subject, so the assistant can offer them right away.
                    return "FAILED: " + e.getMessage() + " " + alternativesTo(action.targetId());
                }
            }
            appointmentService.cancel(action.targetId(), customerId);
            return "CANCELLED: appointment " + action.targetId() + ". A cancellation email is on its way.";
        });
    }

    @Tool(description = "Look up TutorSlot policies and FAQ (hours, booking, cancellation, lateness, subjects, "
            + "session length, reminders, help). Answer policy questions only from what this returns.")
    public String searchPolicies(
            @ToolParam(description = "The student's question, in their own words") String question,
            ToolContext toolContext) {
        Long customerId = customerId(toolContext);
        log.info("AI tool call: tool=searchPolicies user={}", customerId);
        return safely("searchPolicies", customerId, () -> {
            List<String> passages = policySearchService.search(question);
            return passages.isEmpty() ? "No policy information found." : String.join("\n\n", passages);
        });
    }

    // A tool never lets an exception reach the model. The service layer's own business errors
    // (409/404/403 on the web pages) become a short FAILED result the assistant can explain and
    // recover from -- for example by offering other slots. Anything unexpected is logged here,
    // server side only, and the model just gets a generic failure.
    private String safely(String toolName, Long customerId, Supplier<String> body) {
        try {
            return body.get();
        } catch (SlotConflictException | NotFoundException | ForbiddenException e) {
            return "FAILED: " + e.getMessage();
        } catch (RuntimeException e) {
            log.error("AI tool failed unexpectedly: tool={} user={}", toolName, customerId, e);
            return "FAILED: something went wrong on our side. Please try again later.";
        }
    }

    // Reuses the same paged query as the Browse slots page (no new SQL) and filters in Java.
    private List<SlotDto> openSlots(String tutorName, String subjectName, LocalDate from, LocalDate to) {
        List<SlotDto> matches = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            SlotsPageDto slotsPage = availabilitySlotService.getAvailableSlotsPage(null, null, null, page);
            for (SlotDto slot : slotsPage.slots()) {
                LocalDate day = slot.startTime().toLocalDate();
                if (containsIgnoreCase(slot.tutorName(), tutorName)
                        && containsIgnoreCase(slot.subjectName(), subjectName)
                        && (from == null || !day.isBefore(from))
                        && (to == null || !day.isAfter(to))) {
                    matches.add(slot);
                }
            }
            if (page >= slotsPage.totalPages()) {
                break;
            }
        }
        matches.sort(Comparator.comparing(SlotDto::startTime));
        return matches;
    }

    private String alternativesTo(Long takenSlotId) {
        String subject = availabilitySlotService.findSlotSummary(takenSlotId).subjectName();
        List<SlotDto> next = openSlots(null, subject, null, null).stream().limit(3).toList();
        if (next.isEmpty()) {
            return "There are no other open " + subject + " slots.";
        }
        return "Offer the student these next open " + subject + " slots:\n"
                + next.stream().map(AssistantTools::describeSlot).collect(Collectors.joining("\n"));
    }

    private static Long customerId(ToolContext toolContext) {
        return (Long) toolContext.getContext().get(CUSTOMER_ID);
    }

    private static long turn(ToolContext toolContext) {
        return (Long) toolContext.getContext().get(TURN);
    }

    private static LocalDate parseDate(String value) {
        return StringUtils.hasText(value) ? LocalDate.parse(value.trim()) : null;
    }

    private static boolean containsIgnoreCase(String value, String filter) {
        return !StringUtils.hasText(filter) || value.toLowerCase(Locale.ROOT).contains(filter.trim().toLowerCase(Locale.ROOT));
    }

    private static String describeSlot(SlotDto slot) {
        return "slot " + slot.slotId() + ": " + slot.subjectName() + " with " + slot.tutorName() + ", "
                + timeRange(slot.startTime(), slot.endTime());
    }

    private static String describeAppointment(AppointmentSummaryDto appointment) {
        return "appointment " + appointment.appointmentId() + ": " + appointment.subjectName() + " with "
                + appointment.tutorName() + ", " + timeRange(appointment.startTime(), appointment.endTime());
    }

    private static String timeRange(LocalDateTime start, LocalDateTime end) {
        return start.format(DAY_AND_TIME) + " to " + end.format(TIME_ONLY);
    }
}
