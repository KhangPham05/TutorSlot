package com.tutorslot.model;

import java.time.LocalDateTime;

public record User(
        Long userId,
        String email,
        String passwordHash,
        String fullName,
        String role,
        LocalDateTime createdAt
) {
}
