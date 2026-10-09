package com.tutorslot.dto;

import java.time.LocalDateTime;

public record BookingConfirmationDto(
        Long appointmentId,
        String tutorName,
        String subjectName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String notes
) {
}
