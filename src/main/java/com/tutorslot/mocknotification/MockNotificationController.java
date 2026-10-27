package com.tutorslot.mocknotification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

// The "mock external Notification Service" -- a second, separate-in-spirit application living in
// this same project for convenience. It only ever talks JSON over HTTP, never shares a Java class
// with the real caller side (com.tutorslot.notification), and is reached only through
// /mock-notify/**, which Spring Security leaves open (permitAll) and exempts from CSRF, because a
// service-to-service call has no browser session or CSRF token -- the API key is what protects it
// instead.
@RestController
@RequestMapping("/mock-notify")
public class MockNotificationController {

    private static final Logger log = LoggerFactory.getLogger(MockNotificationController.class);
    private static final long SLOW_MODE_DELAY_MS = 5000;

    private final InMemoryNotificationStore store;
    private final NotificationModeHolder modeHolder;
    private final String expectedApiKey;

    public MockNotificationController(InMemoryNotificationStore store, NotificationModeHolder modeHolder,
                                       @Value("${notification.api-key:dev-key}") String expectedApiKey) {
        this.store = store;
        this.modeHolder = modeHolder;
        this.expectedApiKey = expectedApiKey;
    }

    @PostMapping("/notifications")
    public ResponseEntity<Map<String, Object>> receive(@RequestHeader(value = "X-Api-Key", required = false) String apiKey,
                                                         @RequestBody IncomingNotification incoming) throws InterruptedException {
        if (!isValidApiKey(apiKey)) {
            return unauthorized();
        }

        NotificationMode mode = modeHolder.get();
        if (mode == NotificationMode.DOWN) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "UNAVAILABLE"));
        }
        if (mode == NotificationMode.SLOW) {
            Thread.sleep(SLOW_MODE_DELAY_MS);
        }

        Optional<StoredNotification> existing = store.findByIdempotencyKey(incoming.idempotencyKey());
        if (existing.isPresent()) {
            return ResponseEntity.ok(Map.of("status", "DUPLICATE", "notificationId", existing.get().notificationId()));
        }

        log.info("[mock-notify] ACCEPTED key={} to={}", incoming.idempotencyKey(), incoming.recipient());
        StoredNotification saved = store.save(incoming);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("status", "ACCEPTED", "notificationId", saved.notificationId()));
    }

    @PostMapping("/admin/mode/{mode}")
    public ResponseEntity<Map<String, Object>> setMode(@RequestHeader(value = "X-Api-Key", required = false) String apiKey,
                                                         @PathVariable String mode) {
        if (!isValidApiKey(apiKey)) {
            return unauthorized();
        }

        NotificationMode newMode;
        try {
            newMode = NotificationMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "UNKNOWN_MODE", "mode", mode));
        }

        modeHolder.set(newMode);
        log.info("[mock-notify] mode changed to {}", newMode);
        return ResponseEntity.ok(Map.of("status", "OK", "mode", newMode.name()));
    }

    @GetMapping("/notifications")
    public ResponseEntity<?> list(@RequestHeader(value = "X-Api-Key", required = false) String apiKey) {
        if (!isValidApiKey(apiKey)) {
            return unauthorized();
        }
        List<StoredNotification> all = store.findAll();
        return ResponseEntity.ok(all);
    }

    private boolean isValidApiKey(String providedKey) {
        return expectedApiKey.equals(providedKey);
    }

    private ResponseEntity<Map<String, Object>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("status", "UNAUTHORIZED"));
    }
}
