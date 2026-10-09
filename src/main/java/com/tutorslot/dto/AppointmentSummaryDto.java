package com.tutorslot.dto;

import java.time.LocalDateTime;

// tutorName and customerName are always both populated -- the customer's "my appointments" view
// shows tutorName, the provider's "booked with me" view shows customerName, since each side
// already knows who they themselves are.
public record AppointmentSummaryDto(
        Long appointmentId,
        String tutorName,
        String customerName,
        String subjectName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status,
        String notes
) {
}
