package com.tutorslot.service;

import com.tutorslot.dto.AppointmentSummaryDto;
import com.tutorslot.dto.BookingConfirmationDto;
import com.tutorslot.dto.MyAppointmentsDto;
import com.tutorslot.exception.ForbiddenException;
import com.tutorslot.exception.NotFoundException;
import com.tutorslot.exception.SlotConflictException;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
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

        SlotLookups lookups = SlotLookups.load(providerRepository, userRepository, subjectRepository);
        AvailabilitySlot slot = availabilitySlotRepository.findById(appointment.slotId())
                .orElseThrow(() -> new NotFoundException("Slot not found for this appointment."));
        User tutor = lookups.tutorFor(slot);
        Subject subject = lookups.subjectFor(slot);

        return new BookingConfirmationDto(appointment.appointmentId(), tutor.fullName(), subject.name(),
                slot.startTime(), slot.endTime(), appointment.notes());
    }

    // Flips any now-past BOOKED appointments to COMPLETED, then reads -- in the same transaction,
    // so the listing below never shows a BOOKED row whose session is already over.
    @Transactional
    public MyAppointmentsDto getMyAppointments(Long customerId) {
        appointmentRepository.markCompletedPastBookings();

        SlotLookups lookups = SlotLookups.load(providerRepository, userRepository, subjectRepository);
        List<Appointment> appointments = appointmentRepository.findByCustomerId(customerId);

        List<AppointmentSummaryDto> upcoming = appointments.stream()
                .filter(a -> a.status().equals("BOOKED"))
                .map(a -> toSummary(a, lookups))
                .sorted(Comparator.comparing(AppointmentSummaryDto::startTime))
                .toList();

        List<AppointmentSummaryDto> history = appointments.stream()
                .filter(a -> !a.status().equals("BOOKED"))
                .map(a -> toSummary(a, lookups))
                .sorted(Comparator.comparing(AppointmentSummaryDto::startTime).reversed())
                .toList();

        return new MyAppointmentsDto(upcoming, history);
    }

    public void cancel(Long appointmentId, Long customerId) {
        int updated = appointmentRepository.cancel(appointmentId, customerId);
        if (updated > 0) {
            return;
        }

        // The UPDATE affected nothing -- find out why, in the order the spec asks for.
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new NotFoundException("No appointment with id " + appointmentId));
        if (!appointment.customerId().equals(customerId)) {
            throw new ForbiddenException("This appointment doesn't belong to you.");
        }
        throw new SlotConflictException("This appointment can't be cancelled anymore.");
    }

    private AppointmentSummaryDto toSummary(Appointment appointment, SlotLookups lookups) {
        AvailabilitySlot slot = availabilitySlotRepository.findById(appointment.slotId())
                .orElseThrow(() -> new NotFoundException("Slot not found for appointment " + appointment.appointmentId()));
        User tutor = lookups.tutorFor(slot);
        Subject subject = lookups.subjectFor(slot);
        return new AppointmentSummaryDto(appointment.appointmentId(), tutor.fullName(), subject.name(),
                slot.startTime(), slot.endTime(), appointment.status(), appointment.notes());
    }

    // Small bundle of the three reference-data maps every appointment needs enriching with --
    // loaded once per request, not once per row.
    private record SlotLookups(Map<Long, Provider> providersById, Map<Long, User> usersById,
                                Map<Long, Subject> subjectsById) {

        static SlotLookups load(ProviderRepository providerRepository, UserRepository userRepository,
                                 SubjectRepository subjectRepository) {
            return new SlotLookups(
                    providerRepository.findAll().stream().collect(Collectors.toMap(Provider::providerId, Function.identity())),
                    userRepository.findAll().stream().collect(Collectors.toMap(User::userId, Function.identity())),
                    subjectRepository.findAll().stream().collect(Collectors.toMap(Subject::serviceId, Function.identity())));
        }

        User tutorFor(AvailabilitySlot slot) {
            return usersById.get(providersById.get(slot.providerId()).userId());
        }

        Subject subjectFor(AvailabilitySlot slot) {
            return subjectsById.get(slot.serviceId());
        }
    }
}
