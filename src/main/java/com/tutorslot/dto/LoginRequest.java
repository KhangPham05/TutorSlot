package com.tutorslot.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Enter your email.") String email,
        @NotBlank(message = "Enter your password.") String password
) {
    // A record prints every field by default; keep the password out of any log line or error.
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=****]";
    }
}
