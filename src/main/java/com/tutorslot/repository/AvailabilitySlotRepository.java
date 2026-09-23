package com.tutorslot.repository;

import com.tutorslot.model.AvailabilitySlot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AvailabilitySlotRepository {

    private static final RowMapper<AvailabilitySlot> SLOT_ROW_MAPPER = (rs, rowNum) -> new AvailabilitySlot(
            rs.getLong("slot_id"),
            rs.getLong("provider_id"),
            rs.getLong("service_id"),
            rs.getTimestamp("start_time").toLocalDateTime(),
            rs.getTimestamp("end_time").toLocalDateTime()
    );

    private final JdbcTemplate jdbcTemplate;

    public AvailabilitySlotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // A slot is available when it's in the future and has no active (BOOKED) appointment.
    public List<AvailabilitySlot> findAvailable() {
        return jdbcTemplate.query("""
                SELECT slot_id, provider_id, service_id, start_time, end_time
                FROM availability_slots s
                WHERE start_time > NOW()
                  AND NOT EXISTS (
                    SELECT 1 FROM appointments a WHERE a.slot_id = s.slot_id AND a.status = 'BOOKED'
                  )
                ORDER BY start_time
                """, SLOT_ROW_MAPPER);
    }
}
