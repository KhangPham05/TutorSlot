package com.tutorslot.repository;

import com.tutorslot.model.Provider;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ProviderRepository {

    private static final RowMapper<Provider> PROVIDER_ROW_MAPPER = (rs, rowNum) -> new Provider(
            rs.getLong("provider_id"),
            rs.getLong("user_id"),
            rs.getString("title"),
            rs.getString("bio")
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ProviderRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Provider> findAll() {
        return jdbcTemplate.query(
                "SELECT provider_id, user_id, title, bio FROM providers",
                new MapSqlParameterSource(), PROVIDER_ROW_MAPPER);
    }

    public Optional<Provider> findByUserId(Long userId) {
        List<Provider> rows = jdbcTemplate.query(
                "SELECT provider_id, user_id, title, bio FROM providers WHERE user_id = :userId",
                new MapSqlParameterSource("userId", userId), PROVIDER_ROW_MAPPER);
        return rows.stream().findFirst();
    }
}
