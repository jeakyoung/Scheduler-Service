package com.scheduler.infra.persistence.jdbc;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T15 (PLAN.md §4.3b): verifies the V3 migration (schedule_target_db_config join table +
 * dropped schedule_definition.target_db_config_id) directly via Flyway/JDBC, without booting a
 * full Spring context. This is deliberate: T15 (this migration) lands before T16 (the domain/
 * repository rewrite), so JdbcScheduleRepository/ScheduleDefinition still reference the column
 * V3 drops - any @SpringBootTest-based test would fail at context startup (ApplicationRunner
 * -> ScheduleRepository) for reasons that have nothing to do with the migration itself. This
 * class reuses the shared singleton MSSQL container (AbstractRepositoryIntegrationTest.MSSQL)
 * and a throwaway H2 in-memory DB to validate both vendor migration paths in isolation.
 */
class ScheduleTargetDbMultiMigrationTest {

    @Test
    void sqlServerMigratesCleanlyAndEnforcesTheNewConstraints() throws SQLException {
        String jdbcUrl = AbstractRepositoryIntegrationTest.MSSQL.getJdbcUrl() + ";trustServerCertificate=true;";
        String username = AbstractRepositoryIntegrationTest.MSSQL.getUsername();
        String password = AbstractRepositoryIntegrationTest.MSSQL.getPassword();

        Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration/sqlserver")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            verifyMigratedSchema(connection);
        }
    }

    @Test
    void h2MigratesCleanlyAndEnforcesTheNewConstraints() throws SQLException {
        String jdbcUrl = "jdbc:h2:mem:schedule-target-db-multi-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";

        Flyway.configure()
                .dataSource(jdbcUrl, "sa", "")
                .locations("classpath:db/migration/h2")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            verifyMigratedSchema(connection);
        }
    }

    private void verifyMigratedSchema(Connection connection) throws SQLException {
        // schedule_definition no longer has target_db_config_id (column dropped by V3).
        try (ResultSet rs = connection.getMetaData().getColumns(null, null, "schedule_definition", "target_db_config_id")) {
            assertThat(rs.next()).isFalse();
        }

        connection.setAutoCommit(false);
        try {
            long targetDbConfigId = insertTargetDbConfig(connection);
            long scheduleId = insertSchedule(connection);

            insertScheduleTargetDbConfigLink(connection, scheduleId, targetDbConfigId);

            // Duplicate (schedule_id, target_db_config_id) must be rejected by the composite PK.
            assertThatThrownBy(() -> insertScheduleTargetDbConfigLink(connection, scheduleId, targetDbConfigId))
                    .isInstanceOf(SQLException.class);
        } finally {
            connection.rollback();
        }
    }

    private long insertTargetDbConfig(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO target_db_config (name, jdbc_url, username, encrypted_password) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, "migration-test-target");
            ps.setString(2, "jdbc:sqlserver://example-business-db;");
            ps.setString(3, "biz_user");
            ps.setString(4, "encrypted-password");
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private long insertSchedule(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO schedule_definition (name, enabled, cron_expression, sp_name, action_type, action_config) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, "migration-test-schedule");
            ps.setBoolean(2, false);
            ps.setString(3, "0 0 9 ? * MON");
            ps.setString(4, "SP_MIGRATION_TEST");
            ps.setString(5, "DISPLAY_ONLY");
            ps.setString(6, "{}");
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private void insertScheduleTargetDbConfigLink(Connection connection, long scheduleId, long targetDbConfigId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO schedule_target_db_config (schedule_id, target_db_config_id) VALUES (?, ?)")) {
            ps.setLong(1, scheduleId);
            ps.setLong(2, targetDbConfigId);
            ps.executeUpdate();
        }
    }
}
