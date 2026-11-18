package com.tutorslot.dto;

// Whether the chat assistant is on (a Gemini key is set), so the page can say so up front.
public record AssistantStatusDto(boolean enabled) {
}
