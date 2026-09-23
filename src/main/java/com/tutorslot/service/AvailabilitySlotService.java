package com.tutorslot.service;

import com.tutorslot.dto.SlotDto;
import com.tutorslot.model.Provider;
import com.tutorslot.model.Subject;
import com.tutorslot.model.User;
import com.tutorslot.repository.AvailabilitySlotRepository;
import com.tutorslot.repository.ProviderRepository;
import com.tutorslot.repository.SubjectRepository;
import com.tutorslot.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AvailabilitySlotService {

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

    public List<SlotDto> getAvailableSlots() {
        Map<Long, Provider> providersById = providerRepository.findAll().stream()
                .collect(Collectors.toMap(Provider::providerId, Function.identity()));
        Map<Long, User> usersById = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::userId, Function.identity()));
        Map<Long, Subject> subjectsById = subjectRepository.findAll().stream()
                .collect(Collectors.toMap(Subject::serviceId, Function.identity()));

        return availabilitySlotRepository.findAvailable().stream()
                .map(slot -> {
                    Provider provider = providersById.get(slot.providerId());
                    User user = usersById.get(provider.userId());
                    Subject subject = subjectsById.get(slot.serviceId());
                    return new SlotDto(user.fullName(), subject.name(), slot.startTime(), slot.endTime());
                })
                .toList();
    }
}
