package com.tutorslot.ai;

import com.google.genai.errors.ApiException;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.util.StringUtils;

import java.io.InterruptedIOException;
import java.util.stream.Collectors;

// Two small helpers shared by AssistantService and ReminderAgent.
final class GeminiText {

    private GeminiText() {
    }

    // Gemini can send one answer in several pieces, and Spring AI turns each piece into a separate
    // result. ChatClient's content() would return only the first piece (a cut-off sentence), so
    // join them all. Returns null when there is no text at all.
    static String of(ChatResponse response) {
        if (response == null) {
            return null;
        }
        String text = response.getResults().stream()
                .map(generation -> generation.getOutput().getText())
                .filter(StringUtils::hasText)
                .collect(Collectors.joining());
        return StringUtils.hasText(text) ? text : null;
    }

    // For log lines: "Gemini 429 RESOURCE_EXHAUSTED" (quota) or "Gemini 503 UNAVAILABLE"
    // (overloaded) when Google answered with an error, "timeout" when it didn't answer in time,
    // otherwise the root cause's type. Never the message itself, which could echo user text.
    static String describeError(RuntimeException e) {
        Throwable root = e;
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ApiException apiError) {
                return ("Gemini " + apiError.code() + " " + apiError.status()).trim();
            }
            if (cause instanceof InterruptedIOException) {
                return "timeout";
            }
            root = cause;
        }
        return root.getClass().getSimpleName();
    }
}
