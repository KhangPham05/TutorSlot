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
        assertThat(userRepository.findAll()).hasSize(8);
        assertThat(providerRepository.findAll()).hasSize(4);
        assertThat(subjectRepository.findAll()).hasSize(7);
    }

    @Test
    void availableSlotsExcludeBookedOnesAndAreOrderedByStartTime() {
        List<AvailabilitySlot> available = availabilitySlotRepository.findAvailable();

        // 21 seeded slots: 1 past (excluded), 3 of the remaining 20 actively booked ->
        // 17 available (the cancelled one stays available).
        assertThat(available).hasSize(17);
        assertThat(available).isSortedAccordingTo(java.util.Comparator.comparing(AvailabilitySlot::startTime));
    }
}
