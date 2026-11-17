package com.tutorslot.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// The one switch every AI feature checks first. With no real GEMINI_API_KEY (CI, tests, a fresh
// clone), Spring AI still starts on the placeholder key from application.properties, but nothing
// should ever send that placeholder to Google -- so the chat page, the knowledge-file ingestion,
// and the reminder agent all ask isEnabled() and fall back to their non-AI behavior instead.
@Component
public class AiAvailability {

    private static final Logger log = LoggerFactory.getLogger(AiAvailability.class);

    // Must match the default in application.properties: ${GEMINI_API_KEY:not-configured}
    static final String PLACEHOLDER_KEY = "not-configured";

    private final boolean enabled;

    public AiAvailability(@Value("${spring.ai.google.genai.api-key:}") String apiKey) {
        this.enabled = StringUtils.hasText(apiKey) && !PLACEHOLDER_KEY.equals(apiKey.trim());
        // Only ever says whether a key is present -- never logs the key itself.
        log.info("Gemini AI features {}", enabled ? "enabled" : "disabled (no GEMINI_API_KEY set)");
    }

    public boolean isEnabled() {
        return enabled;
    }
}
