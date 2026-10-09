package com.tutorslot.service;

import com.tutorslot.dto.ProviderDashboardDto;
import com.tutorslot.dto.ProviderSlotDto;
import com.tutorslot.dto.ProviderSubjectOptionDto;
import com.tutorslot.exception.ForbiddenException;
import com.tutorslot.exception.NotFoundException;
import com.tutorslot.exception.SlotConflictException;
import com.tutorslot.model.AvailabilitySlot;
import com.tutorslot.model.Subject;
import com.tutorslot.repository.AppointmentRepository;
import com.tutorslot.repository.AvailabilitySlotRepository;
import com.tutorslot.repository.SubjectRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProviderDashboardService {

    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final AppointmentRepository appointmentRepository;
    private final SubjectRepository subjectRepository;

    public ProviderDashboardService(AvailabilitySlotRepository availabilitySlotRepository,
                                     AppointmentRepository appointmentRepository,
                                     SubjectRepository subjectRepository) {
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.appointmentRepository = appointmentRepository;
        this.subjectRepository = subjectRepository;
    }

    public ProviderDashboardDto getDashboard(Long providerId) {
        List<Subject> mySubjects = subjectRepository.findAll().stream()
                .filter(subject -> subject.providerId().equals(providerId))
                .toList();
        Map<Long, String> subjectNamesById = mySubjects.stream()
                .collect(Collectors.toMap(Subject::serviceId, Subject::name));

        List<ProviderSlotDto> slots = availabilitySlotRepository.findByProviderId(providerId).stream()
                .map(slot -> new ProviderSlotDto(slot.slotId(), subjectNamesById.get(slot.serviceId()),
                        slot.startTime(), slot.endTime(), appointmentRepository.hasActiveBooking(slot.slotId())))
                .toList();

        List<ProviderSubjectOptionDto> subjectOptions = mySubjects.stream()
                .map(subject -> new ProviderSubjectOptionDto(subject.serviceId(), subject.name()))
                .toList();

        return new ProviderDashboardDto(slots, subjectOptions);
    }

    public Long createSlot(Long providerId, Long serviceId, LocalDateTime startDateTime) {
        Subject subject = subjectRepository.findById(serviceId)
                .orElseThrow(() -> new NotFoundException("No subject with id " + serviceId));
        if (!subject.providerId().equals(providerId)) {
            throw new ForbiddenException("This subject doesn't belong to you.");
        }

        LocalDateTime endDateTime = startDateTime.plusMinutes(subject.durationMinutes());
        try {
            return availabilitySlotRepository.insert(providerId, serviceId, startDateTime, endDateTime);
        } catch (DuplicateKeyException e) {
            throw new SlotConflictException("You already have a slot at that time.");
        }
    }

    public void removeSlot(Long slotId, Long providerId) {
        AvailabilitySlot slot = availabilitySlotRepository.findById(slotId)
                .orElseThrow(() -> new NotFoundException("No slot with id " + slotId));
        if (!slot.providerId().equals(providerId)) {
            throw new ForbiddenException("This slot doesn't belong to you.");
        }
        if (appointmentRepository.hasAnyAppointmentForSlot(slotId)) {
            throw new SlotConflictException("This slot has booking history and can't be removed.");
        }
        availabilitySlotRepository.delete(slotId, providerId);
    }
}
