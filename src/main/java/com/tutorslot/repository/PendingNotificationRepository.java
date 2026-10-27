package com.tutorslot.repository;

import com.tutorslot.model.PendingNotification;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PendingNotificationRepository {

    private static final RowMapper<PendingNotification> ROW_MAPPER = (rs, rowNum) -> new PendingNotification(
            rs.getLong("pending_id"),
            rs.getString("idempotency_key"),
            rs.getString("recipient"),
            rs.getString("type"),
            rs.getString("message"),
            rs.getLong("appointment_id"),
            rs.getString("status"),
            rs.getInt("attempts"),
            rs.getString("last_error"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PendingNotificationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ON CONFLICT DO NOTHING on the idempotency key: safe to call even if this exact
    // notification was already queued (or already sent and someone calls save again by mistake).
    public void save(String idempotencyKey, String recipient, String type, String message, Long appointmentId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idempotencyKey", idempotencyKey)
                .addValue("recipient", recipient)
                .addValue("type", type)
                .addValue("message", message)
                .addValue("appointmentId", appointmentId);
        jdbcTemplate.update("""
                INSERT INTO pending_notifications (idempotency_key, recipient, type, message, appointment_id)
                VALUES (:idempotencyKey, :recipient, :type, :message, :appointmentId)
                ON CONFLICT (idempotency_key) DO NOTHING
                """, params);
    }

    public List<PendingNotification> findDue(int limit) {
        return jdbcTemplate.query(
                "SELECT * FROM pending_notifications WHERE status = 'PENDING' ORDER BY created_at LIMIT :limit",
                new MapSqlParameterSource("limit", limit), ROW_MAPPER);
    }

    public void markSent(Long pendingId) {
        jdbcTemplate.update(
                "UPDATE pending_notifications SET status = 'SENT', updated_at = NOW() WHERE pending_id = :pendingId",
                new MapSqlParameterSource("pendingId", pendingId));
    }

    public void recordFailure(Long pendingId, String error) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("pendingId", pendingId)
                .addValue("error", error);
        jdbcTemplate.update("""
                UPDATE pending_notifications
                SET attempts = attempts + 1, last_error = :error, updated_at = NOW()
                WHERE pending_id = :pendingId
                """, params);
    }

    public void markFailed(Long pendingId) {
        jdbcTemplate.update(
                "UPDATE pending_notifications SET status = 'FAILED', updated_at = NOW() WHERE pending_id = :pendingId",
                new MapSqlParameterSource("pendingId", pendingId));
    }
}
