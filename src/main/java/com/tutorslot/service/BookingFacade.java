package com.tutorslot.service;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
public class BookingFacade {

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 50;

    private final BookingService bookingService;

    public BookingFacade(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    // This retry loop has to live in a bean separate from BookingService: calling a
    // @Transactional method from another method on the same class bypasses Spring's proxy, so no
    // transaction would actually start, and every retry needs a fresh one. Controllers call this
    // facade, never BookingService directly.
    //
    // Only retries on a temporary lock problem (CannotAcquireLockException is itself a
    // PessimisticLockingFailureException, so one catch covers both -- Java also won't allow
    // catching a type and its own supertype together in one multi-catch). A real conflict
    // (SlotConflictException) is never retried; it propagates immediately.
    public Long book(Long slotId, Long customerId, String notes) {
        PessimisticLockingFailureException lastLockFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return bookingService.book(slotId, customerId, notes);
            } catch (PessimisticLockingFailureException e) {
                lastLockFailure = e;
                sleep(RETRY_DELAY_MS);
            }
        }
        throw lastLockFailure;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
