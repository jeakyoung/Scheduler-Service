package com.scheduler.infra.persistence.jdbc;

import com.scheduler.domain.config.TargetDbConfig;
import com.scheduler.domain.config.TargetDbConfigRepository;
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
public class JdbcTargetDbConfigRepository implements TargetDbConfigRepository {

    private static final RowMapper<TargetDbConfig> ROW_MAPPER = (rs, rowNum) -> new TargetDbConfig(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("jdbc_url"),
            rs.getString("username"),
            rs.getString("encrypted_password"),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("updated_at"))
    );

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcTargetDbConfigRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<TargetDbConfig> findAll() {
        return jdbc.query("SELECT * FROM target_db_config ORDER BY id", ROW_MAPPER);
    }

    @Override
    public Optional<TargetDbConfig> findById(long id) {
        List<TargetDbConfig> results = jdbc.query(
                "SELECT * FROM target_db_config WHERE id = :id",
                new MapSqlParameterSource("id", id),
                ROW_MAPPER
        );
        return results.stream().findFirst();
    }

    @Override
    public TargetDbConfig save(TargetDbConfig config) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("name", config.name())
                .addValue("jdbcUrl", config.jdbcUrl())
                .addValue("username", config.username())
                .addValue("encryptedPassword", config.encryptedPassword());

        jdbc.update(
                "INSERT INTO target_db_config (name, jdbc_url, username, encrypted_password) " +
                        "VALUES (:name, :jdbcUrl, :username, :encryptedPassword)",
                params,
                keyHolder,
                new String[]{"id"}
        );

        long id = keyHolder.getKey().longValue();
        return findById(id).orElseThrow();
    }

    @Override
    public TargetDbConfig update(TargetDbConfig config) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", config.id())
                .addValue("name", config.name())
                .addValue("jdbcUrl", config.jdbcUrl())
                .addValue("username", config.username())
                .addValue("encryptedPassword", config.encryptedPassword());

        jdbc.update(
                "UPDATE target_db_config SET name = :name, jdbc_url = :jdbcUrl, username = :username, " +
                        "encrypted_password = :encryptedPassword, updated_at = SYSUTCDATETIME() WHERE id = :id",
                params
        );
        return findById(config.id()).orElseThrow();
    }

    @Override
    public void deleteById(long id) {
        jdbc.update("DELETE FROM target_db_config WHERE id = :id", new MapSqlParameterSource("id", id));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
