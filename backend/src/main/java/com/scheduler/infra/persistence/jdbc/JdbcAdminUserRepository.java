package com.scheduler.infra.persistence.jdbc;

import com.scheduler.domain.admin.AdminUser;
import com.scheduler.domain.admin.AdminUserRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcAdminUserRepository implements AdminUserRepository {

    private static final RowMapper<AdminUser> ROW_MAPPER = (rs, rowNum) -> new AdminUser(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("role"),
            toInstant(rs.getTimestamp("created_at"))
    );

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAdminUserRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<AdminUser> findAll() {
        return jdbc.query("SELECT * FROM admin_user ORDER BY id", ROW_MAPPER);
    }

    @Override
    public Optional<AdminUser> findByUsername(String username) {
        return jdbc.query(
                "SELECT * FROM admin_user WHERE username = :username",
                new MapSqlParameterSource("username", username),
                ROW_MAPPER
        ).stream().findFirst();
    }

    @Override
    public AdminUser save(AdminUser adminUser) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("username", adminUser.username())
                .addValue("passwordHash", adminUser.passwordHash())
                .addValue("role", adminUser.role());

        jdbc.update(
                "INSERT INTO admin_user (username, password_hash, role) VALUES (:username, :passwordHash, :role)",
                params
        );

        return findByUsername(adminUser.username()).orElseThrow();
    }

    @Override
    public void updatePasswordHash(String username, String newPasswordHash) {
        jdbc.update(
                "UPDATE admin_user SET password_hash = :passwordHash WHERE username = :username",
                new MapSqlParameterSource()
                        .addValue("passwordHash", newPasswordHash)
                        .addValue("username", username)
        );
    }

    @Override
    public boolean existsAny() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM admin_user", new MapSqlParameterSource(), Integer.class);
        return count != null && count > 0;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
