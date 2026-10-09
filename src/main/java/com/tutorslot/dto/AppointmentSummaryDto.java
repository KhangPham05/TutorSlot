package com.tutorslot.dto;

import java.time.LocalDateTime;

public record AppointmentSummaryDto(
        Long appointmentId,
        String tutorName,
        String subjectName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status,
        String notes
) {
}
