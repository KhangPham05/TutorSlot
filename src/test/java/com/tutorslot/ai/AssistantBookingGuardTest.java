package com.tutorslot.ai;

import com.tutorslot.model.AvailabilitySlot;
import com.tutorslot.repository.AvailabilitySlotRepository;
import com.tutorslot.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// The M4 version of BookingConcurrencyTest: proves the assistant's book tool goes through the
// same transactional path as the Book button. Two students each have a pending booking for the
// SAME slot, and both say "yes" at the same moment. The real AssistantTools bean runs against the
// real BookingFacade, row lock, unique index, and PostgreSQL -- and no Gemini call is involved:
// the test calls the tools directly, exactly as Spring AI would after the model picked them.
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AssistantBookingGuardTest {

    @Autowired
    private AssistantTools assistantTools;

    @Autowired
    private PendingActionStore pendingActionStore;

    @Autowired
    private AvailabilitySlotRepository availabilitySlotRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void whenTwoStudentsConfirmTheSameSlotAtOnceExactlyOneIsBooked() throws InterruptedException {
        Long slotId = pickAnAvailableSlotId();
        Long dan = requireCustomerId("dan.student@sjsu.edu");
        Long erin = requireCustomerId("erin.student@sjsu.edu");

        // Turn 1: the assistant proposes the same slot to both students.
        for (Long customerId : List.of(dan, erin)) {
            assertThat(assistantTools.proposeBooking(slotId, turn(customerId, 1))).startsWith("PROPOSED booking");
        }

        // Turn 2: both students said yes, and both confirms run at the same moment.
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(2);
        Map<Long, String> resultByCustomer = new ConcurrentHashMap<>();

        for (Long customerId : List.of(dan, erin)) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    resultByCustomer.put(customerId, assistantTools.confirmPendingAction(turn(customerId, 2)));
                } catch (Exception e) {
                    // A tool must never throw; record it so the assertions below fail clearly.
                    resultByCustomer.put(customerId, "EXCEPTION: " + e);
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown(); // both threads call confirmPendingAction() at the same moment
        boolean finished = doneGate.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).as("both confirms finished in time").isTrue();
        assertThat(resultByCustomer).hasSize(2);

        List<Long> winners = resultByCustomer.entrySet().stream()
                .filter(entry -> entry.getValue().startsWith("BOOKED: appointment"))
                .map(Map.Entry::getKey)
                .toList();
        long alreadyBooked = resultByCustomer.values().stream()
                .filter(result -> result.startsWith("FAILED: This slot is already booked."))
                .count();
        assertThat(winners).as("exactly one confirm should book the slot").hasSize(1);
        assertThat(alreadyBooked).as("the other gets the 'already booked' failure result").isEqualTo(1);

        // Exactly one BOOKED row, and it belongs to the student whose confirm won -- the customer
        // id came from each student's own tool context.
        List<Long> bookedFor = jdbcTemplate.queryForList(
                "SELECT customer_id FROM appointments WHERE slot_id = ? AND status = 'BOOKED'", Long.class, slotId);
        assertThat(bookedFor).containsExactly(winners.get(0));

        // Both proposals are used up, the winner's and the loser's.
        assertThat(pendingActionStore.find(dan)).isEmpty();
        assertThat(pendingActionStore.find(erin)).isEmpty();
    }

    private static ToolContext turn(Long customerId, long turn) {
        return new ToolContext(AssistantTools.toolContext(customerId, turn));
    }

    private Long pickAnAvailableSlotId() {
        List<AvailabilitySlot> available = availabilitySlotRepository.findAvailablePage(null, null, null, 1, 0);
        assertThat(available).as("a seeded, still-open future slot must exist").isNotEmpty();
        return available.get(0).slotId();
    }

    private Long requireCustomerId(String email) {
        return userRepository.findByEmail(email).orElseThrow().userId();
    }
}
