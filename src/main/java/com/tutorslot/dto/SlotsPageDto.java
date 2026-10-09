package com.tutorslot.dto;

import java.util.List;

// Everything the /slots page needs to render one page of results plus the filter form:
// the rows themselves, the dropdown options, and which page/filters are currently selected
// (so the form and the Previous/Next links can stay on the same filters).
public record SlotsPageDto(
        List<SlotDto> slots,
        List<TutorOptionDto> tutors,
        List<SubjectOptionDto> subjects,
        int currentPage,
        int totalPages,
        Long selectedProviderId,
        Long selectedServiceId,
        String selectedDate
) {
}
