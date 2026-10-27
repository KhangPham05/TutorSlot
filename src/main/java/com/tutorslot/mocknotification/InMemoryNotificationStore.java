package com.tutorslot.mocknotification;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

// A real external service would persist these; this one just needs to remember them for the
// lifetime of the process, keyed by idempotency key so a retry never gets stored twice.
@Component
public class InMemoryNotificationStore {

    private final Map<String, StoredNotification> byIdempotencyKey = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    public Optional<StoredNotification> findByIdempotencyKey(String idempotencyKey) {
        return Optional.ofNullable(byIdempotencyKey.get(idempotencyKey));
    }

    public StoredNotification save(IncomingNotification incoming) {
        StoredNotification stored = new StoredNotification(
                nextId.getAndIncrement(),
                incoming.idempotencyKey(),
                incoming.recipient(),
                incoming.type(),
                incoming.message(),
                incoming.appointmentId(),
                Instant.now());
        byIdempotencyKey.put(incoming.idempotencyKey(), stored);
        return stored;
    }

    public List<StoredNotification> findAll() {
        return List.copyOf(byIdempotencyKey.values());
    }
}
