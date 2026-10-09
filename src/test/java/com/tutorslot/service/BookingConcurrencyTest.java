package com.tutorslot.service;

import com.tutorslot.exception.SlotConflictException;
import com.tutorslot.model.AvailabilitySlot;
import com.tutorslot.model.User;
import com.tutorslot.repository.AvailabilitySlotRepository;
import com.tutorslot.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// The key M2 test: proves the double-booking guard actually holds under real concurrency, not
// just in the single-threaded unit tests. Runs against the real local Postgres, not mocks.
//
// @DirtiesContext because this is the only test class that actually writes to the database --
// RepositorySmokeTest and ServiceLayerSmokeTest are pure reads asserting exact seed-data counts,
// and all @SpringBootTest classes share one cached Spring context (and DB state) within a single
// `mvn test` run. Without this, whichever of those runs after this one (JUnit/Surefire class
// order isn't guaranteed) would see a slot missing from the available count and fail. Marking the
// context dirty forces a fresh one -- and a fresh schema.sql + seed.sql reload -- for whatever
// test needs a Spring context next.
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BookingConcurrencyTest {

    @Autowired
    private BookingFacade bookingFacade;

    @Autowired
    private AvailabilitySlotRepository availabilitySlotRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void exactlyOneOfTwoSimultaneousBookingsSucceeds() throws InterruptedException {
        Long slotId = pickAnAvailableSlotId();
        Long customer1 = requireCustomerId("dan.student@sjsu.edu");
        Long customer2 = requireCustomerId("erin.student@sjsu.edu");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(2);
        List<Object> results = Collections.synchronizedList(new ArrayList<>());

        for (Long customerId : List.of(customer1, customer2)) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    results.add(bookingFacade.book(slotId, customerId, null));
                } catch (Exception e) {
                    results.add(e);
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown(); // both threads call BookingFacade.book() at the same moment
        boolean finished = doneGate.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).as("both booking attempts finished in time").isTrue();

        long successCount = results.stream().filter(r -> r instanceof Long).count();
        long conflictCount = results.stream().filter(r -> r instanceof SlotConflictException).count();

        assertThat(successCount).as("exactly one booking should succeed").isEqualTo(1);
        assertThat(conflictCount).as("exactly one booking should be rejected as a conflict").isEqualTo(1);

        Integer bookedCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM appointments WHERE slot_id = ? AND status = 'BOOKED'",
                Integer.class, slotId);
        assertThat(bookedCount).isEqualTo(1);
    }

    // Proves the backstop directly: even bypassing BookingService's row lock entirely, the
    // partial unique index itself refuses a second active booking on the same slot.
    @Test
    void duplicateKeyExceptionIsThrownOnASecondDirectInsert() {
        Long slotId = pickAnAvailableSlotId();
        Long customer1 = requireCustomerId("dan.student@sjsu.edu");
        Long customer2 = requireCustomerId("erin.student@sjsu.edu");

        jdbcTemplate.update(
                "INSERT INTO appointments (slot_id, customer_id, status) VALUES (?, ?, 'BOOKED')",
                slotId, customer1);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO appointments (slot_id, customer_id, status) VALUES (?, ?, 'BOOKED')",
                slotId, customer2))
                .isInstanceOf(DuplicateKeyException.class);
    }

    private Long pickAnAvailableSlotId() {
        List<AvailabilitySlot> available = availabilitySlotRepository.findAvailablePage(null, null, null, 1, 0);
        assertThat(available).as("a seeded, still-open future slot must exist").isNotEmpty();
        return available.get(0).slotId();
    }

    private Long requireCustomerId(String email) {
        User user = userRepository.findByEmail(email).orElseThrow();
        return user.userId();
    }
}
