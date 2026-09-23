package com.tutorslot.model;

public record Provider(
        Long providerId,
        Long userId,
        String title,
        String bio
) {
}
