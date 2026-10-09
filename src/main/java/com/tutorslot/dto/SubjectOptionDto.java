package com.tutorslot.dto;

// label is pre-formatted like "Calculus I (Alice Nguyen)" for the filter dropdown.
public record SubjectOptionDto(
        Long serviceId,
        String label
) {
}
