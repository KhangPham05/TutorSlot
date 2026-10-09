package com.tutorslot.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

public record CreateSlotRequest(
        @NotNull(message = "Pick a subject.") Long serviceId,
        @NotNull(message = "Pick a start date and time.")
        @Future(message = "The start time must be in the future.")
        @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime startDateTime
) {
}
