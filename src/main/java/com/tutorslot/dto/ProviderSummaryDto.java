package com.tutorslot.dto;

import java.util.List;

public record ProviderSummaryDto(
        String fullName,
        String title,
        String bio,
        List<SubjectDto> subjects
) {
}
