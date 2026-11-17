package com.tutorslot.ai;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// At most one pending action per student; a new proposal replaces the previous one. In memory
// only: a restart just forgets unconfirmed proposals, which is the safe direction to fail in.
@Component
public class PendingActionStore {

    private final Map<Long, PendingAction> pendingByUserId = new ConcurrentHashMap<>();

    public void save(Long userId, PendingAction action) {
        pendingByUserId.put(userId, action);
    }

    public Optional<PendingAction> find(Long userId) {
        return Optional.ofNullable(pendingByUserId.get(userId));
    }

    public void clear(Long userId) {
        pendingByUserId.remove(userId);
    }
}
