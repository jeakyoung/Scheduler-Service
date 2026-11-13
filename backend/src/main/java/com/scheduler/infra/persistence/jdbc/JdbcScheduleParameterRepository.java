package com.scheduler.infra.persistence.jdbc;

import com.scheduler.domain.schedule.ParamDataType;
import com.scheduler.domain.schedule.ScheduleParameter;
import com.scheduler.domain.schedule.ScheduleParameterRepository;
import com.scheduler.domain.schedule.ValueSource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class JdbcScheduleParameterRepository implements ScheduleParameterRepository {

    private static final RowMapper<ScheduleParameter> ROW_MAPPER = (rs, rowNum) -> new ScheduleParameter(
            rs.getLong("id"),
            rs.getLong("schedule_id"),
            rs.getInt("param_order"),
            rs.getString("param_name"),
            ParamDataType.valueOf(rs.getString("data_type")),
            ValueSource.valueOf(rs.getString("value_source")),
            rs.getString("literal_value")
    );

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcScheduleParameterRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ScheduleParameter> findByScheduleId(long scheduleId) {
        return jdbc.query(
                "SELECT * FROM schedule_parameter WHERE schedule_id = :scheduleId ORDER BY param_order",
                new MapSqlParameterSource("scheduleId", scheduleId),
                ROW_MAPPER
        );
    }

    @Override
    public List<ScheduleParameter> replaceAll(long scheduleId, List<ScheduleParameter> parameters) {
        deleteByScheduleId(scheduleId);

        for (ScheduleParameter parameter : parameters) {
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("scheduleId", scheduleId)
                    .addValue("paramOrder", parameter.paramOrder())
                    .addValue("paramName", parameter.paramName())
                    .addValue("dataType", parameter.dataType().name())
                    .addValue("valueSource", parameter.valueSource().name())
                    .addValue("literalValue", parameter.literalValue());

            jdbc.update(
                    "INSERT INTO schedule_parameter (schedule_id, param_order, param_name, data_type, value_source, literal_value) " +
                            "VALUES (:scheduleId, :paramOrder, :paramName, :dataType, :valueSource, :literalValue)",
                    params
            );
        }

        return findByScheduleId(scheduleId);
    }

    @Override
    public void deleteByScheduleId(long scheduleId) {
        jdbc.update("DELETE FROM schedule_parameter WHERE schedule_id = :scheduleId", new MapSqlParameterSource("scheduleId", scheduleId));
    }
}
