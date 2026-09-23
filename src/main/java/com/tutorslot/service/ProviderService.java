package com.tutorslot.service;

import com.tutorslot.dto.ProviderSummaryDto;
import com.tutorslot.dto.SubjectDto;
import com.tutorslot.model.Provider;
import com.tutorslot.model.Subject;
import com.tutorslot.model.User;
import com.tutorslot.repository.ProviderRepository;
import com.tutorslot.repository.SubjectRepository;
import com.tutorslot.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProviderService {

    private final ProviderRepository providerRepository;
    private final UserRepository userRepository;
    private final SubjectRepository subjectRepository;

    public ProviderService(ProviderRepository providerRepository, UserRepository userRepository,
                            SubjectRepository subjectRepository) {
        this.providerRepository = providerRepository;
        this.userRepository = userRepository;
        this.subjectRepository = subjectRepository;
    }

    public List<ProviderSummaryDto> getProvidersWithSubjects() {
        Map<Long, User> usersById = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::userId, Function.identity()));
        Map<Long, List<Subject>> subjectsByProviderId = subjectRepository.findAll().stream()
                .collect(Collectors.groupingBy(Subject::providerId));

        return providerRepository.findAll().stream()
                .map(provider -> toDto(provider, usersById.get(provider.userId()),
                        subjectsByProviderId.getOrDefault(provider.providerId(), List.of())))
                .toList();
    }

    private ProviderSummaryDto toDto(Provider provider, User user, List<Subject> subjects) {
        List<SubjectDto> subjectDtos = subjects.stream()
                .map(s -> new SubjectDto(s.name(), s.description(), s.durationMinutes()))
                .toList();
        return new ProviderSummaryDto(user.fullName(), provider.title(), provider.bio(), subjectDtos);
    }
}
