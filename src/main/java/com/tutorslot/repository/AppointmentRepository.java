package com.tutorslot.repository;

import com.tutorslot.model.Appointment;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class AppointmentRepository {

    private static final RowMapper<Appointment> APPOINTMENT_ROW_MAPPER = (rs, rowNum) -> {
        Timestamp cancelledAt = rs.getTimestamp("cancelled_at");
        return new Appointment(
                rs.getLong("appointment_id"),
                rs.getLong("slot_id"),
                rs.getLong("customer_id"),
                rs.getString("status"),
                rs.getString("notes"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                cancelledAt != null ? cancelledAt.toLocalDateTime() : null
        );
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AppointmentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Appointment> findById(Long appointmentId) {
        List<Appointment> rows = jdbcTemplate.query(
                "SELECT appointment_id, slot_id, customer_id, status, notes, created_at, cancelled_at "
                        + "FROM appointments WHERE appointment_id = :appointmentId",
                new MapSqlParameterSource("appointmentId", appointmentId), APPOINTMENT_ROW_MAPPER);
        return rows.stream().findFirst();
    }

    public boolean hasActiveBooking(Long slotId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM appointments WHERE slot_id = :slotId AND status = 'BOOKED'",
                new MapSqlParameterSource("slotId", slotId), Integer.class);
        return count != null && count > 0;
    }

    // Returns the new row's appointment_id (RETURNING, no separate generated-keys dance needed).
    public Long insertBooked(Long slotId, Long customerId, String notes) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("slotId", slotId)
                .addValue("customerId", customerId)
                .addValue("notes", notes);
        return jdbcTemplate.queryForObject(
                "INSERT INTO appointments (slot_id, customer_id, status, notes) "
                        + "VALUES (:slotId, :customerId, 'BOOKED', :notes) RETURNING appointment_id",
                params, Long.class);
    }

    public List<Appointment> findByCustomerId(Long customerId) {
        return jdbcTemplate.query(
                "SELECT appointment_id, slot_id, customer_id, status, notes, created_at, cancelled_at "
                        + "FROM appointments WHERE customer_id = :customerId",
                new MapSqlParameterSource("customerId", customerId), APPOINTMENT_ROW_MAPPER);
    }

    // One atomic statement: only cancels a BOOKED appointment the caller owns, and only while the
    // slot hasn't started yet. Returns the number of rows updated (0 or 1) -- the service decides
    // what 0 means (not found / not yours / already cancelled-or-started) with a follow-up read.
    public int cancel(Long appointmentId, Long customerId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("appointmentId", appointmentId)
                .addValue("customerId", customerId);
        return jdbcTemplate.update("""
                UPDATE appointments a
                SET status = 'CANCELLED', cancelled_at = NOW()
                WHERE a.appointment_id = :appointmentId
                  AND a.customer_id = :customerId
                  AND a.status = 'BOOKED'
                  AND EXISTS (
                    SELECT 1 FROM availability_slots s
                    WHERE s.slot_id = a.slot_id AND s.start_time > NOW()
                  )
                """, params);
    }

    // Run before any appointments listing (customer or provider): a BOOKED appointment whose
    // slot has already ended is really COMPLETED, not still BOOKED. Scoped to all customers, not
    // just the one currently viewing, so the status is consistent no matter who looks first.
    public int markCompletedPastBookings() {
        return jdbcTemplate.update("""
                UPDATE appointments a
                SET status = 'COMPLETED'
                WHERE a.status = 'BOOKED'
                  AND EXISTS (
                    SELECT 1 FROM availability_slots s
                    WHERE s.slot_id = a.slot_id AND s.end_time < NOW()
                  )
                """, new MapSqlParameterSource());
    }
}
