package com.tutorslot.repository;

import com.tutorslot.model.AvailabilitySlot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RepositorySmokeTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private AvailabilitySlotRepository availabilitySlotRepository;

    @Test
    void seedDataLoadsAsExpected() {
        assertThat(userRepository.findAll()).hasSize(6);
        assertThat(providerRepository.findAll()).hasSize(3);
        assertThat(subjectRepository.findAll()).hasSize(6);
    }

    @Test
    void availableSlotsExcludeBookedOnesAndAreOrderedByStartTime() {
        List<AvailabilitySlot> available = availabilitySlotRepository.findAvailable();

        // 18 seeded slots, 3 actively booked -> 15 available (the cancelled one stays available).
        assertThat(available).hasSize(15);
        assertThat(available).isSortedAccordingTo(java.util.Comparator.comparing(AvailabilitySlot::startTime));
    }
}
