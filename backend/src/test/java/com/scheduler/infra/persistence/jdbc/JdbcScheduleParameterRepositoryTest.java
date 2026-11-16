package com.scheduler.infra.persistence.jdbc;

import com.scheduler.domain.schedule.ActionConfigJson;
import com.scheduler.domain.schedule.ActionType;
import com.scheduler.domain.schedule.DisplayOnlyActionConfig;
import com.scheduler.domain.schedule.ParamDataType;
import com.scheduler.domain.schedule.ScheduleDefinition;
import com.scheduler.domain.schedule.ScheduleParameter;
import com.scheduler.domain.schedule.ValueSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcScheduleParameterRepositoryTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private JdbcScheduleParameterRepository repository;

    @Autowired
    private JdbcScheduleRepository scheduleRepository;

    private Long scheduleId;

    @BeforeEach
    void seedSchedule() {
        ScheduleDefinition schedule = scheduleRepository.save(new ScheduleDefinition(
                null, "Parameterized schedule", true, "0 0 9 ? * MON", "Asia/Seoul",
                "SP_SAMPLE_WITH_PARAMS", ActionType.DISPLAY_ONLY,
                ActionConfigJson.write(new DisplayOnlyActionConfig("TABLE", null)),
                null, null, null, "tester"
        ));
        scheduleId = schedule.id();
    }

    @Test
    void scheduleWithNoParametersHasEmptyList() {
        assertThat(repository.findByScheduleId(scheduleId)).isEmpty();
    }

    @Test
    void replaceAllInsertsAndReordersParameters() {
        List<ScheduleParameter> saved = repository.replaceAll(scheduleId, List.of(
                new ScheduleParameter(null, scheduleId, 1, "@FromDate", ParamDataType.DATE, ValueSource.CURRENT_DATE, null),
                new ScheduleParameter(null, scheduleId, 2, "@Status", ParamDataType.STRING, ValueSource.LITERAL, "ACTIVE")
        ));

        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).paramName()).isEqualTo("@FromDate");
        assertThat(saved.get(0).valueSource()).isEqualTo(ValueSource.CURRENT_DATE);
        assertThat(saved.get(1).paramName()).isEqualTo("@Status");
        assertThat(saved.get(1).valueSource()).isEqualTo(ValueSource.LITERAL);
        assertThat(saved.get(1).literalValue()).isEqualTo("ACTIVE");

        List<ScheduleParameter> reloaded = repository.findByScheduleId(scheduleId);
        assertThat(reloaded).hasSize(2);
    }

    @Test
    void replaceAllDropsPreviousRows() {
        repository.replaceAll(scheduleId, List.of(
                new ScheduleParameter(null, scheduleId, 1, "@Old", ParamDataType.STRING, ValueSource.LITERAL, "x")
        ));

        List<ScheduleParameter> replaced = repository.replaceAll(scheduleId, List.of(
                new ScheduleParameter(null, scheduleId, 1, "@New", ParamDataType.STRING, ValueSource.LITERAL, "y")
        ));

        assertThat(replaced).hasSize(1);
        assertThat(replaced.get(0).paramName()).isEqualTo("@New");
    }

    @Test
    void deleteByScheduleIdRemovesAllParameters() {
        repository.replaceAll(scheduleId, List.of(
                new ScheduleParameter(null, scheduleId, 1, "@P1", ParamDataType.INT, ValueSource.LITERAL, "1")
        ));

        repository.deleteByScheduleId(scheduleId);

        assertThat(repository.findByScheduleId(scheduleId)).isEmpty();
    }
}
