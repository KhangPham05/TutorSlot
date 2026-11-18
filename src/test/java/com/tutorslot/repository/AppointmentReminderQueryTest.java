package com.tutorslot.repository;

import com.tutorslot.model.Appointment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// The reminder query against real PostgreSQL and the seed data: appointments 1-3 are BOOKED for
// tomorrow, 4 is CANCELLED, and 5 is BOOKED but already in the past. A 7-day window keeps this
// independent of the time of day the test runs.
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AppointmentReminderQueryTest {

    @Autowired
    private AppointmentRepository appointmentRepository;

    private List<Long> due(int limit) {
        LocalDateTime now = LocalDateTime.now();
        return appointmentRepository.findBookedNeedingReminder(now, now.plusDays(7), limit).stream()
                .map(Appointment::appointmentId)
                .toList();
    }

    @Test
    void findsUpcomingBookedAppointmentsEarliestFirstAndSkipsOnesAlreadyReminded() {
        // Not the cancelled one (4), not the past one (5).
        assertThat(due(10)).containsExactly(1L, 2L, 3L);
        // The per-run cap is the query's LIMIT.
        assertThat(due(2)).containsExactly(1L, 2L);

        appointmentRepository.markReminderSent(1L);

        assertThat(due(10)).containsExactly(2L, 3L);
    }
}
