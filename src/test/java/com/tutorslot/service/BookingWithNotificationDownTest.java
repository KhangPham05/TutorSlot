package com.tutorslot.service;

import com.tutorslot.exception.SlotConflictException;
import com.tutorslot.mocknotification.NotificationMode;
import com.tutorslot.mocknotification.NotificationModeHolder;
import com.tutorslot.model.Appointment;
import com.tutorslot.model.AvailabilitySlot;
import com.tutorslot.model.User;
import com.tutorslot.repository.AppointmentRepository;
import com.tutorslot.repository.AvailabilitySlotRepository;
import com.tutorslot.repository.UserRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// The required M3 integration test: proves bookings survive a notification outage end to end,
// against the real local Postgres, not mocks. notification.base-url points at a closed port
// (nothing listens on port 1) so every attempt in the outer class's tests fails to connect --
// the same ResourceAccessException path a real DNS failure or a service that's simply not there
// would hit. notification.resend-interval-ms is set very high everywhere so the background
// resend job can't race these assertions.
@SpringBootTest(properties = {
        "notification.base-url=http://localhost:1",
        "notification.resend-interval-ms=600000"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BookingWithNotificationDownTest {

    @Autowired
    private BookingFacade bookingFacade;
    @Autowired
    private AvailabilitySlotRepository availabilitySlotRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void bookingSucceedsAndQueuesNotificationWhenServiceIsUnreachable() {
        Long slotId = pickAnAvailableSlotId(availabilitySlotRepository);
        Long customerId = requireCustomerId(userRepository, "dan.student@sjsu.edu");

        Long appointmentId = bookingFacade.book(slotId, customerId, null);

        assertThat(appointmentId).isNotNull();
        Appointment appointment = appointmentRepository.findById(appointmentId).orElseThrow();
        assertThat(appointment.status()).isEqualTo("BOOKED");

        awaitPendingRow(jdbcTemplate, "BOOKED-" + appointmentId);
    }

    // The second booking throws before BookingService ever reaches eventPublisher.publishEvent()
    // (see the success path at the bottom of BookingService.book), so there's no appointmentId
    // for a notification to even be keyed to -- this is what "AFTER_COMMIT drops events from
    // rolled-back transactions" looks like for a conflict specifically: the event for the
    // rejected attempt is never published in the first place, not published-then-discarded.
    @Test
    void conflictingBookingSendsNoNotification() {
        Long slotId = pickAnAvailableSlotId(availabilitySlotRepository);
        Long customer1 = requireCustomerId(userRepository, "dan.student@sjsu.edu");
        Long customer2 = requireCustomerId(userRepository, "erin.student@sjsu.edu");

        Long appointmentId = bookingFacade.book(slotId, customer1, null);
        awaitPendingRow(jdbcTemplate, "BOOKED-" + appointmentId);
        int countBeforeConflict = countPendingRows(jdbcTemplate);

        assertThatThrownBy(() -> bookingFacade.book(slotId, customer2, null))
                .isInstanceOf(SlotConflictException.class);

        assertThat(countPendingRows(jdbcTemplate))
                .as("a rejected booking must not queue any notification")
                .isEqualTo(countBeforeConflict);
    }

    // A separate context: DEFINED_PORT with a fixed, known port lets notification.base-url point
    // back at this same app's own /mock-notify before the context even starts (RANDOM_PORT can't
    // do that -- the port isn't known until after startup, too late for a property the gateway's
    // RestClient bean reads at construction time).
    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
            "server.port=18080",
            "notification.base-url=http://localhost:18080/mock-notify",
            "notification.resend-interval-ms=600000"
    })
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
    class NotificationServiceSlow {

        @Autowired
        private BookingFacade bookingFacade;
        @Autowired
        private NotificationModeHolder modeHolder;
        @Autowired
        private AvailabilitySlotRepository availabilitySlotRepository;
        @Autowired
        private UserRepository userRepository;
        @Autowired
        private JdbcTemplate jdbcTemplate;

        @Test
        void bookingSucceedsAndQueuesNotificationWhenServiceIsSlow() {
            modeHolder.set(NotificationMode.SLOW);
            try {
                Long slotId = pickAnAvailableSlotId(availabilitySlotRepository);
                Long customerId = requireCustomerId(userRepository, "dan.student@sjsu.edu");

                Long appointmentId = bookingFacade.book(slotId, customerId, null);

                assertThat(appointmentId).isNotNull();
                awaitPendingRow(jdbcTemplate, "BOOKED-" + appointmentId);
            } finally {
                modeHolder.set(NotificationMode.UP);
            }
        }
    }

    private static Long pickAnAvailableSlotId(AvailabilitySlotRepository repository) {
        List<AvailabilitySlot> available = repository.findAvailablePage(null, null, null, 1, 0);
        assertThat(available).as("a seeded, still-open future slot must exist").isNotEmpty();
        return available.get(0).slotId();
    }

    private static Long requireCustomerId(UserRepository repository, String email) {
        User user = repository.findByEmail(email).orElseThrow();
        return user.userId();
    }

    private static int countPendingRows(JdbcTemplate jdbcTemplate) {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM pending_notifications", Integer.class);
        return count != null ? count : 0;
    }

    // The listener is @Async, so the PENDING row never appears synchronously with book()
    // returning -- poll instead of asserting immediately. 10 attempts at 1 second comfortably
    // covers the gateway's own worst case (3 attempts, each up to a 1500ms read timeout, plus
    // 200ms/400ms backoff between them -- about 5 seconds total before it gives up and falls back).
    private static void awaitPendingRow(JdbcTemplate jdbcTemplate, String idempotencyKey) {
        for (int i = 0; i < 10; i++) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pending_notifications WHERE idempotency_key = ?",
                    Integer.class, idempotencyKey);
            if (count != null && count > 0) {
                return;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        throw new AssertionError("Expected a pending_notifications row for key " + idempotencyKey
                + " within 10 seconds, but none appeared");
    }
}
