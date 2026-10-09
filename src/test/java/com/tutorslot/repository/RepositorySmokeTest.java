package com.tutorslot.repository;

import com.tutorslot.model.AvailabilitySlot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Comparator;
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
        // 21 seeded slots: 1 past (excluded), 3 of the remaining 20 actively booked ->
        // 17 available (the cancelled one stays available). A limit well past 17 gets them all.
        assertThat(availabilitySlotRepository.countAvailable(null, null, null)).isEqualTo(17);

        List<AvailabilitySlot> available =
                availabilitySlotRepository.findAvailablePage(null, null, null, 100, 0);
        assertThat(available).hasSize(17);
        assertThat(available).isSortedAccordingTo(Comparator.comparing(AvailabilitySlot::startTime));
    }

    @Test
    void findAvailablePageRespectsLimitAndOffset() {
        List<AvailabilitySlot> firstPage =
                availabilitySlotRepository.findAvailablePage(null, null, null, 10, 0);
        List<AvailabilitySlot> secondPage =
                availabilitySlotRepository.findAvailablePage(null, null, null, 10, 10);

        assertThat(firstPage).hasSize(10);
        assertThat(secondPage).hasSize(7); // 17 total - 10 on page 1
        assertThat(firstPage.get(9).startTime()).isBefore(secondPage.get(0).startTime());
    }

    @Test
    void findAvailablePageFiltersByProvider() {
        Long aliceProviderId = providerRepository.findAll().stream()
                .filter(p -> userRepository.findAll().stream()
                        .anyMatch(u -> u.userId().equals(p.userId()) && u.fullName().equals("Alice Nguyen")))
                .findFirst().orElseThrow().providerId();

        List<AvailabilitySlot> aliceSlots =
                availabilitySlotRepository.findAvailablePage(aliceProviderId, null, null, 100, 0);

        assertThat(aliceSlots).isNotEmpty();
        assertThat(aliceSlots).allMatch(slot -> slot.providerId().equals(aliceProviderId));
    }
}
