package com.tutorslot.model;

import java.time.LocalDateTime;

public record Appointment(
        Long appointmentId,
        Long slotId,
        Long customerId,
        String status,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime cancelledAt
) {
}
