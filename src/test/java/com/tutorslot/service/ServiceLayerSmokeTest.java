package com.tutorslot.service;

import com.tutorslot.dto.ProviderSummaryDto;
import com.tutorslot.dto.SlotDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ServiceLayerSmokeTest {

    @Autowired
    private ProviderService providerService;

    @Autowired
    private AvailabilitySlotService availabilitySlotService;

    @Test
    void providersComeBackWithTheirSubjectsGrouped() {
        List<ProviderSummaryDto> providers = providerService.getProvidersWithSubjects();

        assertThat(providers).hasSize(3);
        assertThat(providers).allSatisfy(provider -> {
            assertThat(provider.fullName()).isNotBlank();
            assertThat(provider.subjects()).isNotEmpty();
        });

        ProviderSummaryDto alice = providers.stream()
                .filter(p -> p.fullName().equals("Alice Nguyen"))
                .findFirst()
                .orElseThrow();
        assertThat(alice.subjects()).extracting("name")
                .containsExactlyInAnyOrder("Calculus I", "Intro to Java");
    }

    @Test
    void availableSlotsComeBackWithTutorAndSubjectJoinedIn() {
        List<SlotDto> slots = availabilitySlotService.getAvailableSlots();

        assertThat(slots).hasSize(15);
        assertThat(slots).allSatisfy(slot -> {
            assertThat(slot.tutorName()).isNotBlank();
            assertThat(slot.subjectName()).isNotBlank();
        });
        assertThat(slots).isSortedAccordingTo(Comparator.comparing(SlotDto::startTime));
    }
}
