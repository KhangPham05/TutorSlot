package com.tutorslot.service;

import com.tutorslot.exception.ForbiddenException;
import com.tutorslot.exception.SlotConflictException;
import com.tutorslot.model.AvailabilitySlot;
import com.tutorslot.model.Subject;
import com.tutorslot.repository.AppointmentRepository;
import com.tutorslot.repository.AvailabilitySlotRepository;
import com.tutorslot.repository.SubjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderDashboardServiceTest {

    @Mock
    private AvailabilitySlotRepository availabilitySlotRepository;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private SubjectRepository subjectRepository;

    @InjectMocks
    private ProviderDashboardService providerDashboardService;

    private static final Long MY_PROVIDER_ID = 1L;
    private static final Long OTHER_PROVIDER_ID = 2L;
    private static final Long SERVICE_ID = 10L;
    private static final Long SLOT_ID = 20L;

    @Test
    void createSlotRejectsAnotherProvidersService() {
        Subject someoneElsesSubject = new Subject(SERVICE_ID, OTHER_PROVIDER_ID, "Physics I", null, 60);
        when(subjectRepository.findById(SERVICE_ID)).thenReturn(Optional.of(someoneElsesSubject));

        assertThatThrownBy(() -> providerDashboardService.createSlot(
                MY_PROVIDER_ID, SERVICE_ID, LocalDateTime.now().plusDays(1)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void removeSlotRejectsASlotWithAppointmentHistory() {
        AvailabilitySlot mySlot = new AvailabilitySlot(SLOT_ID, MY_PROVIDER_ID, SERVICE_ID,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1));
        when(availabilitySlotRepository.findById(SLOT_ID)).thenReturn(Optional.of(mySlot));
        when(appointmentRepository.hasAnyAppointmentForSlot(SLOT_ID)).thenReturn(true);

        assertThatThrownBy(() -> providerDashboardService.removeSlot(SLOT_ID, MY_PROVIDER_ID))
                .isInstanceOf(SlotConflictException.class);
    }

    @Test
    void removeSlotRejectsAnotherProvidersSlot() {
        AvailabilitySlot someoneElsesSlot = new AvailabilitySlot(SLOT_ID, OTHER_PROVIDER_ID, SERVICE_ID,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1));
        when(availabilitySlotRepository.findById(SLOT_ID)).thenReturn(Optional.of(someoneElsesSlot));

        assertThatThrownBy(() -> providerDashboardService.removeSlot(SLOT_ID, MY_PROVIDER_ID))
                .isInstanceOf(ForbiddenException.class);
    }
}
