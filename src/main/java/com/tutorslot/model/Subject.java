package com.tutorslot.model;

// Mirrors the `services` table. Named Subject, not Service, to avoid clashing with
// Spring's own @Service annotation used throughout the service layer.
public record Subject(
        Long serviceId,
        Long providerId,
        String name,
        String description,
        Integer durationMinutes
) {
}
