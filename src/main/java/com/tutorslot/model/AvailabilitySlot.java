package com.tutorslot.model;

import java.time.LocalDateTime;

public record AvailabilitySlot(
        Long slotId,
        Long providerId,
        Long serviceId,
        LocalDateTime startTime,
        LocalDateTime endTime
) {
}
