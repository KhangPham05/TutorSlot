package com.tutorslot.repository;

import com.tutorslot.model.AvailabilitySlot;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
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
