package com.tutorslot.dto;

import jakarta.validation.constraints.NotBlank;

public record AssistantMessageRequest(@NotBlank(message = "Type a message.") String message) {

    // A record prints every field by default; a student's message never goes in a log line.
    @Override
    public String toString() {
        return "AssistantMessageRequest[message=****]";
    }
}
