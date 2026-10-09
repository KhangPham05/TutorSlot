package com.tutorslot.dto;

// Just the provider's own subjects, for the create-slot form dropdown -- unlike
// SubjectOptionDto, no tutor name needed since it's always their own name.
public record ProviderSubjectOptionDto(
        Long serviceId,
        String name
) {
}
