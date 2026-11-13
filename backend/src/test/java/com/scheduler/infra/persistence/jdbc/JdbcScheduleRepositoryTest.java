package com.scheduler.infra.persistence.jdbc;

import com.scheduler.domain.config.TargetDbConfig;
import com.scheduler.domain.schedule.ActionConfigJson;
import com.scheduler.domain.schedule.ActionType;
import com.scheduler.domain.schedule.ApiCallActionConfig;
import com.scheduler.domain.schedule.DisplayOnlyActionConfig;
import com.scheduler.domain.schedule.GroupingMode;
import com.scheduler.domain.schedule.ScheduleDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcScheduleRepositoryTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private JdbcScheduleRepository repository;

    @Autowired
    private JdbcTargetDbConfigRepository targetDbConfigRepository;

    private Long targetDbConfigId;

    @BeforeEach
    void seedTargetDbConfig() {
        TargetDbConfig config = targetDbConfigRepository.save(new TargetDbConfig(
                null, "Test Target DB", "jdbc:sqlserver://example-business-db;", "biz_user", "enc", null, null
        ));
        targetDbConfigId = config.id();
    }

    @Test
    void savesAndReloadsApiCallScheduleWithJsonRoundTrip() {
        String actionConfigJson = ActionConfigJson.write(new ApiCallActionConfig(
                "POST",
                "https://notify.example.com/api/push",
                Map.of("Content-Type", "application/json; charset=UTF-8", "X-Api-Key", "${cred:SAMPLE_API_KEY}"),
                GroupingMode.PER_ROW,
                null,
                "{\"empList\":[\"${EMPLOYEE_NO}\"]}"
        ));

        ScheduleDefinition saved = repository.save(new ScheduleDefinition(
                null,
                "Hourly notice",
                true,
                "0 0/30 * * * MON-FRI",
                "Asia/Seoul",
                "SP_SAMPLE_NOTICE_LIST",
                targetDbConfigId,
                ActionType.API_CALL,
                actionConfigJson,
                "sample description",
                null, null, "tester"
        ));

        ScheduleDefinition reloaded = repository.findById(saved.id()).orElseThrow();
        assertThat(reloaded.name()).isEqualTo("Hourly notice");
        assertThat(reloaded.enabled()).isTrue();
        assertThat(reloaded.cronExpression()).isEqualTo("0 0/30 * * * MON-FRI");
        assertThat(reloaded.actionType()).isEqualTo(ActionType.API_CALL);
        assertThat(reloaded.targetDbConfigId()).isEqualTo(targetDbConfigId);

        ApiCallActionConfig roundTripped = ActionConfigJson.readApiCall(reloaded.actionConfig());
        assertThat(roundTripped.method()).isEqualTo("POST");
        assertThat(roundTripped.url()).isEqualTo("https://notify.example.com/api/push");
        assertThat(roundTripped.groupingMode()).isEqualTo(GroupingMode.PER_ROW);
        assertThat(roundTripped.headers()).containsEntry("X-Api-Key", "${cred:SAMPLE_API_KEY}");
        assertThat(roundTripped.bodyTemplate()).contains("${EMPLOYEE_NO}");
    }

    @Test
    void savesAndReloadsDisplayOnlyScheduleWithJsonRoundTrip() {
        String actionConfigJson = ActionConfigJson.write(new DisplayOnlyActionConfig(
                "TABLE", List.of("EMPLOYEE_NO", "EA_TITLE", "EXE_SEQ")
        ));

        ScheduleDefinition saved = repository.save(new ScheduleDefinition(
                null,
                "Display only sample",
                false,
                "0 30 8 * * MON-FRI",
                "Asia/Seoul",
                "SP_SAMPLE_DISPLAY",
                targetDbConfigId,
                ActionType.DISPLAY_ONLY,
                actionConfigJson,
                null,
                null, null, "tester"
        ));

        ScheduleDefinition reloaded = repository.findById(saved.id()).orElseThrow();
        assertThat(reloaded.actionType()).isEqualTo(ActionType.DISPLAY_ONLY);

        DisplayOnlyActionConfig roundTripped = ActionConfigJson.readDisplayOnly(reloaded.actionConfig());
        assertThat(roundTripped.displayFormat()).isEqualTo("TABLE");
        assertThat(roundTripped.columns()).containsExactly("EMPLOYEE_NO", "EA_TITLE", "EXE_SEQ");
    }

    @Test
    void findAllEnabledOnlyReturnsEnabledRows() {
        repository.save(baseSchedule("Enabled one", true));
        repository.save(baseSchedule("Disabled one", false));

        List<ScheduleDefinition> enabled = repository.findAllEnabled();

        assertThat(enabled).extracting(ScheduleDefinition::name).contains("Enabled one");
        assertThat(enabled).extracting(ScheduleDefinition::name).doesNotContain("Disabled one");
    }

    @Test
    void updateEnabledTogglesFlag() {
        ScheduleDefinition saved = repository.save(baseSchedule("Toggle me", false));

        repository.updateEnabled(saved.id(), true, "tester");

        assertThat(repository.findById(saved.id()).orElseThrow().enabled()).isTrue();
    }

    @Test
    void deleteByIdRemovesRow() {
        ScheduleDefinition saved = repository.save(baseSchedule("Delete me", false));

        repository.deleteById(saved.id());

        assertThat(repository.findById(saved.id())).isEmpty();
    }

    private ScheduleDefinition baseSchedule(String name, boolean enabled) {
        return new ScheduleDefinition(
                null,
                name,
                enabled,
                "0 0 9 * * MON",
                "Asia/Seoul",
                "SP_SAMPLE",
                targetDbConfigId,
                ActionType.DISPLAY_ONLY,
                ActionConfigJson.write(new DisplayOnlyActionConfig("TABLE", null)),
                null,
                null, null, "tester"
        );
    }
}
