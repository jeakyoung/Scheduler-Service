package com.scheduler.infra.persistence.jdbc;

import com.scheduler.domain.schedule.ScheduleTargetDbConfigRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class JdbcScheduleTargetDbConfigRepository implements ScheduleTargetDbConfigRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcScheduleTargetDbConfigRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Long> findTargetDbConfigIdsByScheduleId(long scheduleId) {
        return jdbc.queryForList(
                "SELECT target_db_config_id FROM schedule_target_db_config WHERE schedule_id = :scheduleId ORDER BY target_db_config_id",
                new MapSqlParameterSource("scheduleId", scheduleId),
                Long.class
        );
    }

    @Override
    public List<Long> findScheduleIdsByTargetDbConfigId(long targetDbConfigId) {
        return jdbc.queryForList(
                "SELECT schedule_id FROM schedule_target_db_config WHERE target_db_config_id = :targetDbConfigId ORDER BY schedule_id",
                new MapSqlParameterSource("targetDbConfigId", targetDbConfigId),
                Long.class
        );
    }

    @Override
    @Transactional
    public void replaceForSchedule(long scheduleId, List<Long> targetDbConfigIds) {
        jdbc.update(
                "DELETE FROM schedule_target_db_config WHERE schedule_id = :scheduleId",
                new MapSqlParameterSource("scheduleId", scheduleId)
        );

        if (targetDbConfigIds.isEmpty()) {
            return;
        }

        MapSqlParameterSource[] batchParams = targetDbConfigIds.stream()
                .map(targetDbConfigId -> new MapSqlParameterSource()
                        .addValue("scheduleId", scheduleId)
                        .addValue("targetDbConfigId", targetDbConfigId))
                .toArray(MapSqlParameterSource[]::new);

        jdbc.batchUpdate(
                "INSERT INTO schedule_target_db_config (schedule_id, target_db_config_id) VALUES (:scheduleId, :targetDbConfigId)",
                batchParams
        );
    }
}
