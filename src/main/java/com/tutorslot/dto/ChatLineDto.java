package com.tutorslot.dto;

// One line of the assistant page's transcript: who said it ("You" or "Assistant") and the text.
public record ChatLineDto(String speaker, String text) {
}
