package com.tutorslot.repository;

import com.tutorslot.model.Provider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ProviderRepository {

    private static final RowMapper<Provider> PROVIDER_ROW_MAPPER = (rs, rowNum) -> new Provider(
            rs.getLong("provider_id"),
            rs.getLong("user_id"),
            rs.getString("title"),
            rs.getString("bio")
    );

    private final JdbcTemplate jdbcTemplate;

    public ProviderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Provider> findAll() {
        return jdbcTemplate.query(
                "SELECT provider_id, user_id, title, bio FROM providers",
                PROVIDER_ROW_MAPPER);
    }
}
