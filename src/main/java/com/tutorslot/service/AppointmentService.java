package com.tutorslot.service;

import com.tutorslot.dto.BookingConfirmationDto;
import com.tutorslot.exception.ForbiddenException;
import com.tutorslot.exception.NotFoundException;
import com.tutorslot.model.Appointment;
import com.tutorslot.model.AvailabilitySlot;
import com.tutorslot.model.Provider;
import com.tutorslot.model.Subject;
import com.tutorslot.model.User;
import com.tutorslot.repository.AppointmentRepository;
import com.tutorslot.repository.AvailabilitySlotRepository;
import com.tutorslot.repository.ProviderRepository;
import com.tutorslot.repository.SubjectRepository;
import com.tutorslot.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final ProviderRepository providerRepository;
    private final UserRepository userRepository;
    private final SubjectRepository subjectRepository;

    public AppointmentService(AppointmentRepository appointmentRepository,
                               AvailabilitySlotRepository availabilitySlotRepository,
                               ProviderRepository providerRepository, UserRepository userRepository,
                               SubjectRepository subjectRepository) {
        this.appointmentRepository = appointmentRepository;
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.providerRepository = providerRepository;
        this.userRepository = userRepository;
        this.subjectRepository = subjectRepository;
    }

    public BookingConfirmationDto getConfirmation(Long appointmentId, Long requestingUserId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new NotFoundException("No appointment with id " + appointmentId));
        if (!appointment.customerId().equals(requestingUserId)) {
            throw new ForbiddenException("This appointment doesn't belong to you.");
        }

        AvailabilitySlot slot = availabilitySlotRepository.findById(appointment.slotId())
                .orElseThrow(() -> new NotFoundException("Slot not found for this appointment."));

        Map<Long, Provider> providersById = providerRepository.findAll().stream()
                .collect(Collectors.toMap(Provider::providerId, Function.identity()));
        Map<Long, User> usersById = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::userId, Function.identity()));
        Map<Long, Subject> subjectsById = subjectRepository.findAll().stream()
                .collect(Collectors.toMap(Subject::serviceId, Function.identity()));

        Provider provider = providersById.get(slot.providerId());
        User tutor = usersById.get(provider.userId());
        Subject subject = subjectsById.get(slot.serviceId());

        return new BookingConfirmationDto(appointment.appointmentId(), tutor.fullName(), subject.name(),
                slot.startTime(), slot.endTime(), appointment.notes());
    }
}
