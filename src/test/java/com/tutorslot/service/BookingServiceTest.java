package com.tutorslot.service;

import com.tutorslot.exception.NotFoundException;
import com.tutorslot.exception.SlotConflictException;
import com.tutorslot.model.AvailabilitySlot;
import com.tutorslot.repository.AppointmentRepository;
import com.tutorslot.repository.AvailabilitySlotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private AvailabilitySlotRepository availabilitySlotRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @InjectMocks
    private BookingService bookingService;

    private static final Long SLOT_ID = 1L;
    private static final Long CUSTOMER_ID = 2L;

    private AvailabilitySlot futureSlot() {
        return new AvailabilitySlot(SLOT_ID, 10L, 20L,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1));
    }

    @Test
    void booksAnOpenFutureSlot() {
        when(availabilitySlotRepository.lockForUpdate(SLOT_ID)).thenReturn(Optional.of(futureSlot()));
        when(appointmentRepository.hasActiveBooking(SLOT_ID)).thenReturn(false);
        when(appointmentRepository.insertBooked(SLOT_ID, CUSTOMER_ID, "notes")).thenReturn(99L);

        Long appointmentId = bookingService.book(SLOT_ID, CUSTOMER_ID, "notes");

        assertThat(appointmentId).isEqualTo(99L);
        verify(appointmentRepository).insertBooked(SLOT_ID, CUSTOMER_ID, "notes");
    }

    @Test
    void rejectsASlotThatAlreadyHasABookedAppointment() {
        when(availabilitySlotRepository.lockForUpdate(SLOT_ID)).thenReturn(Optional.of(futureSlot()));
        when(appointmentRepository.hasActiveBooking(SLOT_ID)).thenReturn(true);

        assertThatThrownBy(() -> bookingService.book(SLOT_ID, CUSTOMER_ID, null))
                .isInstanceOf(SlotConflictException.class);

        verify(appointmentRepository, never()).insertBooked(anyLong(), anyLong(), any());
    }

    @Test
    void rejectsAPastSlot() {
        AvailabilitySlot pastSlot = new AvailabilitySlot(SLOT_ID, 10L, 20L,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().minusDays(1).plusHours(1));
        when(availabilitySlotRepository.lockForUpdate(SLOT_ID)).thenReturn(Optional.of(pastSlot));

        assertThatThrownBy(() -> bookingService.book(SLOT_ID, CUSTOMER_ID, null))
                .isInstanceOf(SlotConflictException.class);

        verify(appointmentRepository, never()).hasActiveBooking(anyLong());
    }

    @Test
    void rejectsAMissingSlot() {
        when(availabilitySlotRepository.lockForUpdate(SLOT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.book(SLOT_ID, CUSTOMER_ID, null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void translatesDuplicateKeyExceptionIntoSlotConflictException() {
        when(availabilitySlotRepository.lockForUpdate(SLOT_ID)).thenReturn(Optional.of(futureSlot()));
        when(appointmentRepository.hasActiveBooking(SLOT_ID)).thenReturn(false);
        when(appointmentRepository.insertBooked(SLOT_ID, CUSTOMER_ID, null))
                .thenThrow(new DuplicateKeyException("uq_one_active_booking_per_slot"));

        assertThatThrownBy(() -> bookingService.book(SLOT_ID, CUSTOMER_ID, null))
                .isInstanceOf(SlotConflictException.class);
    }
}
