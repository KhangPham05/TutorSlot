package com.tutorslot.dto;

import java.time.LocalDateTime;

public record SlotDto(
        Long slotId,
        String tutorName,
        String subjectName,
        LocalDateTime startTime,
        LocalDateTime endTime
) {
}
