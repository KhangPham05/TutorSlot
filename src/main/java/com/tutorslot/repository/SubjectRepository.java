package com.tutorslot.repository;

import com.tutorslot.model.Subject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

// Queries the `services` table (see Subject model for the naming note).
@Repository
public class SubjectRepository {

    private static final RowMapper<Subject> SUBJECT_ROW_MAPPER = (rs, rowNum) -> new Subject(
            rs.getLong("service_id"),
            rs.getLong("provider_id"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getInt("duration_minutes")
    );

    private final JdbcTemplate jdbcTemplate;

    public SubjectRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Subject> findAll() {
        return jdbcTemplate.query(
                "SELECT service_id, provider_id, name, description, duration_minutes FROM services",
                SUBJECT_ROW_MAPPER);
    }
}
