package com.tutorslot.service;

import com.tutorslot.dto.ProviderSummaryDto;
import com.tutorslot.dto.SlotDto;
import com.tutorslot.dto.SlotsPageDto;
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

        assertThat(providers).hasSize(4);
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
    void firstPageHasTenOfSeventeenAvailableSlots() {
        SlotsPageDto page = availabilitySlotService.getAvailableSlotsPage(null, null, null, 1);

        assertThat(page.currentPage()).isEqualTo(1);
        assertThat(page.totalPages()).isEqualTo(2); // 17 slots, page size 10
        assertThat(page.slots()).hasSize(10);
        assertThat(page.slots()).allSatisfy(slot -> {
            assertThat(slot.slotId()).isNotNull();
            assertThat(slot.tutorName()).isNotBlank();
            assertThat(slot.subjectName()).isNotBlank();
        });
        assertThat(page.slots()).isSortedAccordingTo(Comparator.comparing(SlotDto::startTime));
    }

    @Test
    void secondPageHasTheRemainingSevenSlots() {
        SlotsPageDto page = availabilitySlotService.getAvailableSlotsPage(null, null, null, 2);

        assertThat(page.slots()).hasSize(7);
    }

    @Test
    void pageBelowOneFallsBackToPageOne() {
        SlotsPageDto page = availabilitySlotService.getAvailableSlotsPage(null, null, null, 0);

        assertThat(page.currentPage()).isEqualTo(1);
        assertThat(page.slots()).hasSize(10);
    }

    @Test
    void pagePastTheEndIsEmptyNotAnError() {
        SlotsPageDto page = availabilitySlotService.getAvailableSlotsPage(null, null, null, 99);

        assertThat(page.slots()).isEmpty();
    }

    @Test
    void dropdownOptionsCoverEveryProviderAndSubject() {
        SlotsPageDto page = availabilitySlotService.getAvailableSlotsPage(null, null, null, 1);

        assertThat(page.tutors()).hasSize(4);
        assertThat(page.subjects()).hasSize(7);
        assertThat(page.subjects()).extracting("label")
                .contains("Calculus I (Alice Nguyen)");
    }
}
