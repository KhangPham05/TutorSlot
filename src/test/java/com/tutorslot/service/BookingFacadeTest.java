package com.tutorslot.service;

import com.tutorslot.exception.SlotConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingFacadeTest {

    @Mock
    private BookingService bookingService;

    @InjectMocks
    private BookingFacade bookingFacade;

    private static final Long SLOT_ID = 1L;
    private static final Long CUSTOMER_ID = 2L;

    @Test
    void retriesOnCannotAcquireLockExceptionAndEventuallySucceeds() {
        when(bookingService.book(SLOT_ID, CUSTOMER_ID, null))
                .thenThrow(new CannotAcquireLockException("lock timeout"))
                .thenReturn(42L);

        Long appointmentId = bookingFacade.book(SLOT_ID, CUSTOMER_ID, null);

        assertThat(appointmentId).isEqualTo(42L);
        verify(bookingService, times(2)).book(SLOT_ID, CUSTOMER_ID, null);
    }

    @Test
    void neverRetriesSlotConflictException() {
        when(bookingService.book(SLOT_ID, CUSTOMER_ID, null)).thenThrow(new SlotConflictException("already booked"));

        assertThatThrownBy(() -> bookingFacade.book(SLOT_ID, CUSTOMER_ID, null))
                .isInstanceOf(SlotConflictException.class);

        verify(bookingService, times(1)).book(SLOT_ID, CUSTOMER_ID, null);
    }

    @Test
    void givesUpAfterMaxAttempts() {
        when(bookingService.book(SLOT_ID, CUSTOMER_ID, null))
                .thenThrow(new CannotAcquireLockException("still locked"));

        assertThatThrownBy(() -> bookingFacade.book(SLOT_ID, CUSTOMER_ID, null))
                .isInstanceOf(PessimisticLockingFailureException.class);

        verify(bookingService, times(3)).book(SLOT_ID, CUSTOMER_ID, null);
    }
}
