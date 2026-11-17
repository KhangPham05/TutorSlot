package com.tutorslot.ai;

import com.tutorslot.dto.AppointmentSummaryDto;
import com.tutorslot.dto.MyAppointmentsDto;
import com.tutorslot.dto.SlotDto;
import com.tutorslot.dto.SlotsPageDto;
import com.tutorslot.exception.SlotConflictException;
import com.tutorslot.service.AppointmentService;
import com.tutorslot.service.AvailabilitySlotService;
import com.tutorslot.service.BookingFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantToolsTest {

    private static final Long CUSTOMER_ID = 5L;
    private static final Long SLOT_ID = 7L;
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 17, 13, 0);

    @Mock
    private AvailabilitySlotService availabilitySlotService;
    @Mock
    private AppointmentService appointmentService;
    @Mock
    private BookingFacade bookingFacade;
    @Mock
    private PolicySearchService policySearchService;

    private PendingActionStore pendingActionStore;
    private AssistantTools tools;

    @BeforeEach
    void setUp() {
        pendingActionStore = new PendingActionStore();
        tools = new AssistantTools(availabilitySlotService, appointmentService, bookingFacade,
                pendingActionStore, policySearchService);
    }

    private static ToolContext turn(long turn) {
        return new ToolContext(AssistantTools.toolContext(CUSTOMER_ID, turn));
    }

    private void proposeSlotOnTurn(long turn) {
        when(availabilitySlotService.findSlotSummary(SLOT_ID))
                .thenReturn(new SlotDto(SLOT_ID, "Alice Nguyen", "Calculus I", START, START.plusHours(1)));
        assertThat(tools.proposeBooking(SLOT_ID, turn(turn))).startsWith("PROPOSED booking: slot 7: Calculus I");
    }

    @Test
    void confirmOnTheSameTurnAsTheProposalIsRefusedAndNeverBooks() {
        proposeSlotOnTurn(1);

        String result = tools.confirmPendingAction(turn(1));

        assertThat(result).isEqualTo("FAILED: ask the student to confirm first");
        verifyNoInteractions(bookingFacade);
        // Kept, so the student's "yes" on the next turn can still confirm it.
        assertThat(pendingActionStore.find(CUSTOMER_ID)).isPresent();
    }

    @Test
    void confirmOnALaterTurnBooksWithTheCustomerIdFromTheToolContext() {
        proposeSlotOnTurn(1);
        when(bookingFacade.book(SLOT_ID, CUSTOMER_ID, null)).thenReturn(42L);

        String result = tools.confirmPendingAction(turn(2));

        assertThat(result).startsWith("BOOKED: appointment 42");
        verify(bookingFacade).book(SLOT_ID, CUSTOMER_ID, null);
        assertThat(pendingActionStore.find(CUSTOMER_ID)).isEmpty();
    }

    @Test
    void aSlotConflictBecomesAFailureResultNotAnException() {
        proposeSlotOnTurn(1);
        when(bookingFacade.book(SLOT_ID, CUSTOMER_ID, null))
                .thenThrow(new SlotConflictException("This slot is already booked."));

        String result = tools.confirmPendingAction(turn(2));

        assertThat(result).isEqualTo("FAILED: This slot is already booked.");
        assertThat(pendingActionStore.find(CUSTOMER_ID)).isEmpty();
    }

    @Test
    void confirmingAProposedCancellationOnALaterTurnCancelsIt() {
        AppointmentSummaryDto mine = new AppointmentSummaryDto(12L, "Alice Nguyen", "Dan Kim", "Calculus I",
                START, START.plusHours(1), "BOOKED", null);
        when(appointmentService.getMyAppointments(CUSTOMER_ID)).thenReturn(new MyAppointmentsDto(List.of(mine), List.of()));

        assertThat(tools.proposeCancellation(12L, turn(3))).startsWith("PROPOSED cancellation: appointment 12");
        String result = tools.confirmPendingAction(turn(4));

        assertThat(result).startsWith("CANCELLED: appointment 12");
        verify(appointmentService).cancel(12L, CUSTOMER_ID);
    }

    @Test
    void proposingToCancelAnAppointmentThatIsNotTheStudentsUpcomingOneFails() {
        when(appointmentService.getMyAppointments(CUSTOMER_ID)).thenReturn(new MyAppointmentsDto(List.of(), List.of()));

        String result = tools.proposeCancellation(99L, turn(1));

        assertThat(result).startsWith("FAILED: appointment 99 is not one of the student's upcoming appointments");
        assertThat(pendingActionStore.find(CUSTOMER_ID)).isEmpty();
        verify(appointmentService, never()).cancel(99L, CUSTOMER_ID);
    }

    @Test
    void findAvailableSlotsFiltersByNameAndDateAcrossPages() {
        SlotDto calculusDay1 = new SlotDto(1L, "Alice Nguyen", "Calculus I", START, START.plusHours(1));
        SlotDto dataStructuresDay1 = new SlotDto(2L, "Bob Martinez", "Data Structures", START, START.plusHours(1));
        SlotDto calculusDay5 = new SlotDto(3L, "Alice Nguyen", "Calculus I", START.plusDays(4), START.plusDays(4).plusHours(1));
        when(availabilitySlotService.getAvailableSlotsPage(null, null, null, 1))
                .thenReturn(page(List.of(calculusDay1, dataStructuresDay1), 1, 2));
        when(availabilitySlotService.getAvailableSlotsPage(null, null, null, 2))
                .thenReturn(page(List.of(calculusDay5), 2, 2));

        String result = tools.findAvailableSlots(null, "CALCULUS", null, "2026-11-18", turn(1));

        assertThat(result).isEqualTo("slot 1: Calculus I with Alice Nguyen, Tue Nov 17, 2026 1:00 PM to 2:00 PM");
        // Only 2 pages exist, so a 3rd is never requested.
        verify(availabilitySlotService, never()).getAvailableSlotsPage(null, null, null, 3);
    }

    @Test
    void findAvailableSlotsRejectsABadDateWithoutQuerying() {
        String result = tools.findAvailableSlots(null, null, "next tuesday", null, turn(1));

        assertThat(result).isEqualTo("FAILED: dates must look like 2026-11-17");
        verifyNoInteractions(availabilitySlotService);
    }

    private static SlotsPageDto page(List<SlotDto> slots, int currentPage, int totalPages) {
        return new SlotsPageDto(slots, List.of(), List.of(), currentPage, totalPages, null, null, null);
    }
}
