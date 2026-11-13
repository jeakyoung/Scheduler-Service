package com.scheduler.infra.persistence.jdbc;

import com.scheduler.domain.schedule.ActionType;
import com.scheduler.domain.schedule.ScheduleDefinition;
import com.scheduler.domain.schedule.ScheduleRepository;
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
public class JdbcScheduleRepository implements ScheduleRepository {

    private static final RowMapper<ScheduleDefinition> ROW_MAPPER = (rs, rowNum) -> new ScheduleDefinition(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getBoolean("enabled"),
            rs.getString("cron_expression"),
            rs.getString("timezone"),
            rs.getString("sp_name"),
            rs.getLong("target_db_config_id"),
            ActionType.valueOf(rs.getString("action_type")),
            rs.getString("action_config"),
            rs.getString("description"),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("updated_at")),
            rs.getString("updated_by")
    );

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcScheduleRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ScheduleDefinition> findAll() {
        return jdbc.query("SELECT * FROM schedule_definition ORDER BY id", ROW_MAPPER);
    }

    @Override
    public List<ScheduleDefinition> findAllEnabled() {
        return jdbc.query("SELECT * FROM schedule_definition WHERE enabled = 1 ORDER BY id", ROW_MAPPER);
    }

    @Override
    public Optional<ScheduleDefinition> findById(long id) {
        return jdbc.query("SELECT * FROM schedule_definition WHERE id = :id", new MapSqlParameterSource("id", id), ROW_MAPPER)
                .stream().findFirst();
    }

    @Override
    public ScheduleDefinition save(ScheduleDefinition schedule) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = toParams(schedule);

        jdbc.update(
                "INSERT INTO schedule_definition " +
                        "(name, enabled, cron_expression, timezone, sp_name, target_db_config_id, action_type, action_config, description, updated_by) " +
                        "VALUES (:name, :enabled, :cronExpression, :timezone, :spName, :targetDbConfigId, :actionType, :actionConfig, :description, :updatedBy)",
                params,
                keyHolder,
                new String[]{"id"}
        );

        long id = keyHolder.getKey().longValue();
        return findById(id).orElseThrow();
    }

    @Override
    public ScheduleDefinition update(ScheduleDefinition schedule) {
        MapSqlParameterSource params = toParams(schedule).addValue("id", schedule.id());

        jdbc.update(
                "UPDATE schedule_definition SET name = :name, enabled = :enabled, cron_expression = :cronExpression, " +
                        "timezone = :timezone, sp_name = :spName, target_db_config_id = :targetDbConfigId, " +
                        "action_type = :actionType, action_config = :actionConfig, description = :description, " +
                        "updated_by = :updatedBy, updated_at = SYSUTCDATETIME() WHERE id = :id",
                params
        );
        return findById(schedule.id()).orElseThrow();
    }

    @Override
    public void deleteById(long id) {
        jdbc.update("DELETE FROM schedule_definition WHERE id = :id", new MapSqlParameterSource("id", id));
    }

    @Override
    public void updateEnabled(long id, boolean enabled, String updatedBy) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("enabled", enabled)
                .addValue("updatedBy", updatedBy);

        jdbc.update(
                "UPDATE schedule_definition SET enabled = :enabled, updated_by = :updatedBy, updated_at = SYSUTCDATETIME() WHERE id = :id",
                params
        );
    }

    private static MapSqlParameterSource toParams(ScheduleDefinition schedule) {
        return new MapSqlParameterSource()
                .addValue("name", schedule.name())
                .addValue("enabled", schedule.enabled())
                .addValue("cronExpression", schedule.cronExpression())
                .addValue("timezone", schedule.timezone())
                .addValue("spName", schedule.spName())
                .addValue("targetDbConfigId", schedule.targetDbConfigId())
                .addValue("actionType", schedule.actionType().name())
                .addValue("actionConfig", schedule.actionConfig())
                .addValue("description", schedule.description())
                .addValue("updatedBy", schedule.updatedBy());
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
