package com.scheduler.infra.persistence.jdbc;

import com.scheduler.domain.config.Credential;
import com.scheduler.domain.config.CredentialRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcCredentialRepository implements CredentialRepository {

    private static final RowMapper<Credential> ROW_MAPPER = (rs, rowNum) -> new Credential(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("encrypted_value"),
            rs.getString("description"),
            toInstant(rs.getTimestamp("updated_at")),
            rs.getString("updated_by")
    );

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcCredentialRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Credential> findAll() {
        return jdbc.query("SELECT * FROM credential ORDER BY id", ROW_MAPPER);
    }

    @Override
    public Optional<Credential> findById(long id) {
        return jdbc.query("SELECT * FROM credential WHERE id = :id", new MapSqlParameterSource("id", id), ROW_MAPPER)
                .stream().findFirst();
    }

    @Override
    public Optional<Credential> findByName(String name) {
        return jdbc.query("SELECT * FROM credential WHERE name = :name", new MapSqlParameterSource("name", name), ROW_MAPPER)
                .stream().findFirst();
    }

    @Override
    public Credential save(Credential credential) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("name", credential.name())
                .addValue("encryptedValue", credential.encryptedValue())
                .addValue("description", credential.description())
                .addValue("updatedBy", credential.updatedBy());

        jdbc.update(
                "INSERT INTO credential (name, encrypted_value, description, updated_by) " +
                        "VALUES (:name, :encryptedValue, :description, :updatedBy)",
                params,
                keyHolder,
                new String[]{"id"}
        );

        long id = keyHolder.getKey().longValue();
        return findById(id).orElseThrow();
    }

    @Override
    public Credential update(Credential credential) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", credential.id())
                .addValue("name", credential.name())
                .addValue("encryptedValue", credential.encryptedValue())
                .addValue("description", credential.description())
                .addValue("updatedBy", credential.updatedBy());

        jdbc.update(
                "UPDATE credential SET name = :name, encrypted_value = :encryptedValue, description = :description, " +
                        "updated_by = :updatedBy, updated_at = SYSUTCDATETIME() WHERE id = :id",
                params
        );
        return findById(credential.id()).orElseThrow();
    }

    @Override
    public void deleteById(long id) {
        jdbc.update("DELETE FROM credential WHERE id = :id", new MapSqlParameterSource("id", id));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
