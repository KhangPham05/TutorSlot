package com.tutorslot.service;

import com.tutorslot.exception.SlotConflictException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
public class BookingFacade {

    private static final Logger log = LoggerFactory.getLogger(BookingFacade.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 50;

    private final BookingService bookingService;
    private final MeterRegistry meterRegistry;

    public BookingFacade(BookingService bookingService, MeterRegistry meterRegistry) {
        this.bookingService = bookingService;
        this.meterRegistry = meterRegistry;
    }

    // tutorslot.booking.latency times every call, success or failure, so the report can show
    // average and max booking time including whatever lock retries happened underneath.
    // tutorslot.bookings counts the three outcomes a caller actually cares about: a real booking,
    // a business conflict (409), or anything else going wrong.
    public Long book(Long slotId, Long customerId, String notes) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            Long appointmentId = attemptBook(slotId, customerId, notes);
            meterRegistry.counter("tutorslot.bookings", "outcome", "success").increment();
            return appointmentId;
        } catch (SlotConflictException e) {
            meterRegistry.counter("tutorslot.bookings", "outcome", "conflict").increment();
            throw e;
        } catch (RuntimeException e) {
            meterRegistry.counter("tutorslot.bookings", "outcome", "error").increment();
            throw e;
        } finally {
            sample.stop(meterRegistry.timer("tutorslot.booking.latency"));
        }
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
    private Long attemptBook(Long slotId, Long customerId, String notes) {
        PessimisticLockingFailureException lastLockFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return bookingService.book(slotId, customerId, notes);
            } catch (PessimisticLockingFailureException e) {
                lastLockFailure = e;
                log.warn("Booking lock failure, retrying: slot={} attempt={}/{}", slotId, attempt, MAX_ATTEMPTS);
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
