package com.tutorslot.dto;

import java.time.LocalDateTime;

public record ProviderSlotDto(
        Long slotId,
        String subjectName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        boolean booked
) {
}
