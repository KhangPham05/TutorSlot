package com.tutorslot.dto;

// The logged-in user as the frontend sees them: enough for the navbar and role-based pages.
public record MeDto(String email, String fullName, String role) {
}
