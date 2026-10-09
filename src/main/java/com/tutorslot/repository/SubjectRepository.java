package com.tutorslot.repository;

import com.tutorslot.model.Subject;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SubjectRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Subject> findAll() {
        return jdbcTemplate.query(
                "SELECT service_id, provider_id, name, description, duration_minutes FROM services",
                new MapSqlParameterSource(), SUBJECT_ROW_MAPPER);
    }

    public Optional<Subject> findById(Long serviceId) {
        List<Subject> rows = jdbcTemplate.query(
                "SELECT service_id, provider_id, name, description, duration_minutes FROM services "
                        + "WHERE service_id = :serviceId",
                new MapSqlParameterSource("serviceId", serviceId), SUBJECT_ROW_MAPPER);
        return rows.stream().findFirst();
    }
}
