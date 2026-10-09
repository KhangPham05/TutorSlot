package com.tutorslot.service;

import com.tutorslot.dto.SlotDto;
import com.tutorslot.dto.SlotsPageDto;
import com.tutorslot.dto.SubjectOptionDto;
import com.tutorslot.dto.TutorOptionDto;
import com.tutorslot.exception.NotFoundException;
import com.tutorslot.model.AvailabilitySlot;
import com.tutorslot.model.Provider;
import com.tutorslot.model.Subject;
import com.tutorslot.model.User;
import com.tutorslot.repository.AvailabilitySlotRepository;
import com.tutorslot.repository.ProviderRepository;
import com.tutorslot.repository.SubjectRepository;
import com.tutorslot.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AvailabilitySlotService {

    private static final int PAGE_SIZE = 10;

    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final ProviderRepository providerRepository;
    private final UserRepository userRepository;
    private final SubjectRepository subjectRepository;

    public AvailabilitySlotService(AvailabilitySlotRepository availabilitySlotRepository,
                                    ProviderRepository providerRepository, UserRepository userRepository,
                                    SubjectRepository subjectRepository) {
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.providerRepository = providerRepository;
        this.userRepository = userRepository;
        this.subjectRepository = subjectRepository;
    }

    public SlotsPageDto getAvailableSlotsPage(Long providerId, Long serviceId, LocalDate date, int page) {
        // Below page 1, just show page 1. Past the last page, let the query come back empty --
        // never an error either way.
        int currentPage = Math.max(page, 1);
        int offset = (currentPage - 1) * PAGE_SIZE;

        Map<Long, Provider> providersById = providerRepository.findAll().stream()
                .collect(Collectors.toMap(Provider::providerId, Function.identity()));
        Map<Long, User> usersById = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::userId, Function.identity()));
        Map<Long, Subject> subjectsById = subjectRepository.findAll().stream()
                .collect(Collectors.toMap(Subject::serviceId, Function.identity()));

        int totalCount = availabilitySlotRepository.countAvailable(providerId, serviceId, date);
        int totalPages = Math.max(1, (int) Math.ceil(totalCount / (double) PAGE_SIZE));

        List<AvailabilitySlot> pageOfSlots =
                availabilitySlotRepository.findAvailablePage(providerId, serviceId, date, PAGE_SIZE, offset);

        List<SlotDto> slotDtos = pageOfSlots.stream()
                .map(slot -> toSlotDto(slot, providersById, usersById, subjectsById))
                .toList();

        List<TutorOptionDto> tutors = buildTutorOptions(providersById, usersById);
        List<SubjectOptionDto> subjects = buildSubjectOptions(providersById, usersById, subjectsById);

        return new SlotsPageDto(slotDtos, tutors, subjects, currentPage, totalPages,
                providerId, serviceId, date != null ? date.toString() : null);
    }

    // For the book-form page: one slot's display info. A plain lookup, not locked -- the
    // authoritative future/already-booked check happens in BookingService at submit time.
    public SlotDto findSlotSummary(Long slotId) {
        AvailabilitySlot slot = availabilitySlotRepository.findById(slotId)
                .orElseThrow(() -> new NotFoundException("No slot with id " + slotId));

        Map<Long, Provider> providersById = providerRepository.findAll().stream()
                .collect(Collectors.toMap(Provider::providerId, Function.identity()));
        Map<Long, User> usersById = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::userId, Function.identity()));
        Map<Long, Subject> subjectsById = subjectRepository.findAll().stream()
                .collect(Collectors.toMap(Subject::serviceId, Function.identity()));

        return toSlotDto(slot, providersById, usersById, subjectsById);
    }

    private SlotDto toSlotDto(AvailabilitySlot slot, Map<Long, Provider> providersById,
                               Map<Long, User> usersById, Map<Long, Subject> subjectsById) {
        Provider provider = providersById.get(slot.providerId());
        User user = usersById.get(provider.userId());
        Subject subject = subjectsById.get(slot.serviceId());
        return new SlotDto(slot.slotId(), user.fullName(), subject.name(), slot.startTime(), slot.endTime());
    }

    private List<TutorOptionDto> buildTutorOptions(Map<Long, Provider> providersById, Map<Long, User> usersById) {
        return providersById.values().stream()
                .map(provider -> new TutorOptionDto(provider.providerId(), usersById.get(provider.userId()).fullName()))
                .sorted(Comparator.comparing(TutorOptionDto::name))
                .toList();
    }

    private List<SubjectOptionDto> buildSubjectOptions(Map<Long, Provider> providersById, Map<Long, User> usersById,
                                                         Map<Long, Subject> subjectsById) {
        return subjectsById.values().stream()
                .map(subject -> {
                    Provider provider = providersById.get(subject.providerId());
                    User user = usersById.get(provider.userId());
                    String label = subject.name() + " (" + user.fullName() + ")";
                    return new SubjectOptionDto(subject.serviceId(), label);
                })
                .sorted(Comparator.comparing(SubjectOptionDto::label))
                .toList();
    }
}
