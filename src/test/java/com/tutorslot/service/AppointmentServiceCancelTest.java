package com.tutorslot.service;

import com.tutorslot.exception.ForbiddenException;
import com.tutorslot.exception.SlotConflictException;
import com.tutorslot.model.Appointment;
import com.tutorslot.repository.AppointmentRepository;
import com.tutorslot.repository.AvailabilitySlotRepository;
import com.tutorslot.repository.ProviderRepository;
import com.tutorslot.repository.SubjectRepository;
import com.tutorslot.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceCancelTest {

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private AvailabilitySlotRepository availabilitySlotRepository;
    @Mock
    private ProviderRepository providerRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SubjectRepository subjectRepository;

    @InjectMocks
    private AppointmentService appointmentService;

    private static final Long APPOINTMENT_ID = 1L;
    private static final Long OWNER_ID = 2L;
    private static final Long OTHER_USER_ID = 3L;

    private Appointment appointment(Long customerId, String status) {
        return new Appointment(APPOINTMENT_ID, 10L, customerId, status, null,
                LocalDateTime.now().minusDays(1), null);
    }

    @Test
    void ownerCanCancel() {
        when(appointmentRepository.cancel(APPOINTMENT_ID, OWNER_ID)).thenReturn(1);

        assertThatCode(() -> appointmentService.cancel(APPOINTMENT_ID, OWNER_ID)).doesNotThrowAnyException();
    }

    @Test
    void nonOwnerGetsForbidden() {
        when(appointmentRepository.cancel(APPOINTMENT_ID, OTHER_USER_ID)).thenReturn(0);
        when(appointmentRepository.findById(APPOINTMENT_ID))
                .thenReturn(Optional.of(appointment(OWNER_ID, "BOOKED")));

        assertThatThrownBy(() -> appointmentService.cancel(APPOINTMENT_ID, OTHER_USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void alreadyCancelledAppointmentGetsConflict() {
        when(appointmentRepository.cancel(APPOINTMENT_ID, OWNER_ID)).thenReturn(0);
        when(appointmentRepository.findById(APPOINTMENT_ID))
                .thenReturn(Optional.of(appointment(OWNER_ID, "CANCELLED")));

        assertThatThrownBy(() -> appointmentService.cancel(APPOINTMENT_ID, OWNER_ID))
                .isInstanceOf(SlotConflictException.class);
    }
}
