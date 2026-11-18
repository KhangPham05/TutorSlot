package com.tutorslot.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BookingRequest(
        @NotNull(message = "Pick a slot.") Long slotId,
        @Size(max = 1000, message = "Notes can be at most 1000 characters.") String notes
) {
}
