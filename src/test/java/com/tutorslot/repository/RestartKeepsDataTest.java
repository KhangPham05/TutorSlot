package com.tutorslot.repository;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// Proves a restart keeps the data. The only database step the app runs at startup is
// flyway.migrate(), so this test writes a booking and a queued notification, runs migrate() again
// exactly as the next startup would, and checks that nothing ran and every row is still there.
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RestartKeepsDataTest {

    private static final List<String> TABLES = List.of(
            "users", "providers", "services", "availability_slots", "appointments", "pending_notifications");

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void runningTheMigrationsAgainOnRestartKeepsEveryRow() {
        // Data the old startup script would have wiped: a new booking on an open slot, and a
        // notification still waiting in the outbox.
        Long appointmentId = jdbcTemplate.queryForObject("""
                INSERT INTO appointments (slot_id, customer_id)
                VALUES ((SELECT MIN(s.slot_id) FROM availability_slots s WHERE NOT EXISTS (
                            SELECT 1 FROM appointments a WHERE a.slot_id = s.slot_id AND a.status = 'BOOKED')),
                        (SELECT user_id FROM users WHERE email = 'dan.student@sjsu.edu'))
                RETURNING appointment_id
                """, Long.class);
        jdbcTemplate.update("""
                INSERT INTO pending_notifications (idempotency_key, recipient, type, message, appointment_id)
                VALUES ('RESTART-TEST', 'dan.student@sjsu.edu', 'APPOINTMENT_BOOKED', 'queued before restart', ?)
                """, appointmentId);
        Map<String, Long> before = rowCounts();

        MigrateResult restart = flyway.migrate();

        assertThat(restart.migrationsExecuted).as("a restart applies nothing new").isZero();
        assertThat(rowCounts()).isEqualTo(before);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM appointments WHERE appointment_id = ?", String.class, appointmentId))
                .isEqualTo("BOOKED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pending_notifications WHERE idempotency_key = 'RESTART-TEST'", Long.class))
                .isEqualTo(1L);
    }

    private Map<String, Long> rowCounts() {
        return TABLES.stream().collect(Collectors.toMap(
                table -> table,
                table -> jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class)));
    }
}
