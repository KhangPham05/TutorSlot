package com.tutorslot.ai;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

// The turn counter behind the confirm rule (see AssistantTools.confirmPendingAction): +1 for
// every message a student sends, from the web page and the API alike, so the rule holds whichever
// one they use. Kept per user and never reset, so it only ever goes up, even after "New
// conversation" or logging in again.
@Component
public class AssistantTurns {

    private final Map<Long, AtomicLong> turnsByUserId = new ConcurrentHashMap<>();

    public long next(Long userId) {
        return turnsByUserId.computeIfAbsent(userId, id -> new AtomicLong()).incrementAndGet();
    }
}
