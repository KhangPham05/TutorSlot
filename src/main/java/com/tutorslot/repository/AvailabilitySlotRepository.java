package com.tutorslot.repository;

import com.tutorslot.model.AvailabilitySlot;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class AvailabilitySlotRepository {

    private static final RowMapper<AvailabilitySlot> SLOT_ROW_MAPPER = (rs, rowNum) -> new AvailabilitySlot(
            rs.getLong("slot_id"),
            rs.getLong("provider_id"),
            rs.getLong("service_id"),
            rs.getTimestamp("start_time").toLocalDateTime(),
            rs.getTimestamp("end_time").toLocalDateTime()
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AvailabilitySlotRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AvailabilitySlot> findAvailablePage(Long providerId, Long serviceId, LocalDate date,
                                                      int limit, int offset) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = availableWhereClause(providerId, serviceId, date, params);
        params.addValue("limit", limit);
        params.addValue("offset", offset);

        String sql = "SELECT slot_id, provider_id, service_id, start_time, end_time "
                + "FROM availability_slots s " + where
                + " ORDER BY start_time LIMIT :limit OFFSET :offset";
        return jdbcTemplate.query(sql, params, SLOT_ROW_MAPPER);
    }

    public Optional<AvailabilitySlot> findById(Long slotId) {
        List<AvailabilitySlot> rows = jdbcTemplate.query(
                "SELECT slot_id, provider_id, service_id, start_time, end_time "
                        + "FROM availability_slots WHERE slot_id = :slotId",
                new MapSqlParameterSource("slotId", slotId), SLOT_ROW_MAPPER);
        return rows.stream().findFirst();
    }

    // Locks the row for the rest of the caller's transaction -- a second transaction trying to
    // lock the same slot_id blocks here until the first one commits or rolls back. This is what
    // makes the booking flow's double-booking check reliable, on top of the DB-level unique index.
    public Optional<AvailabilitySlot> lockForUpdate(Long slotId) {
        List<AvailabilitySlot> rows = jdbcTemplate.query(
                "SELECT slot_id, provider_id, service_id, start_time, end_time "
                        + "FROM availability_slots WHERE slot_id = :slotId FOR UPDATE",
                new MapSqlParameterSource("slotId", slotId), SLOT_ROW_MAPPER);
        return rows.stream().findFirst();
    }

    public List<AvailabilitySlot> findByProviderId(Long providerId) {
        return jdbcTemplate.query(
                "SELECT slot_id, provider_id, service_id, start_time, end_time "
                        + "FROM availability_slots WHERE provider_id = :providerId ORDER BY start_time",
                new MapSqlParameterSource("providerId", providerId), SLOT_ROW_MAPPER);
    }

    // Returns the new row's slot_id. A duplicate (provider_id, start_time) throws
    // DuplicateKeyException -- the service translates that into a 409.
    public Long insert(Long providerId, Long serviceId, LocalDateTime startTime, LocalDateTime endTime) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("providerId", providerId)
                .addValue("serviceId", serviceId)
                .addValue("startTime", startTime)
                .addValue("endTime", endTime);
        return jdbcTemplate.queryForObject(
                "INSERT INTO availability_slots (provider_id, service_id, start_time, end_time) "
                        + "VALUES (:providerId, :serviceId, :startTime, :endTime) RETURNING slot_id",
                params, Long.class);
    }

    // provider_id is included in the WHERE as a second ownership check, on top of the one the
    // service already did -- belt and suspenders, same spirit as the cancel statement.
    public int delete(Long slotId, Long providerId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("slotId", slotId)
                .addValue("providerId", providerId);
        return jdbcTemplate.update(
                "DELETE FROM availability_slots WHERE slot_id = :slotId AND provider_id = :providerId",
                params);
    }

    public int countAvailable(Long providerId, Long serviceId, LocalDate date) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = availableWhereClause(providerId, serviceId, date, params);

        String sql = "SELECT count(*) FROM availability_slots s " + where;
        Integer count = jdbcTemplate.queryForObject(sql, params, Integer.class);
        return count != null ? count : 0;
    }

    // Shared "available" condition (future, no active BOOKED appointment) plus whichever of the
    // three optional filters were given. Filter values always go in as named parameters -- only
    // the fixed SQL fragments themselves are appended based on which filters are present.
    private String availableWhereClause(Long providerId, Long serviceId, LocalDate date,
                                          MapSqlParameterSource params) {
        StringBuilder where = new StringBuilder(
                "WHERE start_time > NOW() AND NOT EXISTS ("
                        + "SELECT 1 FROM appointments a WHERE a.slot_id = s.slot_id AND a.status = 'BOOKED')");

        if (providerId != null) {
            where.append(" AND provider_id = :providerId");
            params.addValue("providerId", providerId);
        }
        if (serviceId != null) {
            where.append(" AND service_id = :serviceId");
            params.addValue("serviceId", serviceId);
        }
        if (date != null) {
            where.append(" AND start_time >= :dayStart AND start_time < :dayEnd");
            params.addValue("dayStart", date.atStartOfDay());
            params.addValue("dayEnd", date.plusDays(1).atStartOfDay());
        }
        return where.toString();
    }
}
