package com.tutorslot.ai;

// A booking or cancellation the assistant has proposed but not carried out yet. targetId is the
// slot id (BOOK) or the appointment id (CANCEL). turn is the number of the student's message
// during which it was proposed -- see AssistantTools.confirmPendingAction for why that matters.
public record PendingAction(Type type, Long targetId, long turn) {

    public enum Type {
        BOOK,
        CANCEL
    }
}
