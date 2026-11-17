package com.scheduler.web.api;

import com.scheduler.domain.config.TargetDbConfig;
import com.scheduler.domain.config.TargetDbConfigRepository;
import com.scheduler.domain.schedule.ActionConfigJson;
import com.scheduler.domain.schedule.ActionType;
import com.scheduler.domain.schedule.DisplayOnlyActionConfig;
import com.scheduler.domain.schedule.ScheduleDefinition;
import com.scheduler.domain.schedule.ScheduleRepository;
import com.scheduler.domain.schedule.ScheduleTargetDbConfigRepository;
import com.scheduler.engine.quartz.AbstractQuartzIntegrationTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * REVIEW.md N4: ScheduleController.create/update now wrap scheduleRepository.save/update and
 * scheduleTargetDbConfigRepository.replaceForSchedule in one @Transactional boundary - this
 * verifies that a replaceForSchedule failure rolls back the schedule row too, rather than
 * leaving a committed schedule with zero target DBs. Gets its own context (MockitoSpyBean
 * changes the bean definitions) rather than sharing ScheduleControllerTest's.
 */
@AutoConfigureMockMvc
@WithMockUser(username = "test-admin", roles = "ADMIN")
class ScheduleTargetDbTransactionTest extends AbstractQuartzIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private TargetDbConfigRepository targetDbConfigRepository;

    @MockitoSpyBean
    private ScheduleTargetDbConfigRepository scheduleTargetDbConfigRepository;

    private long seedTargetDbConfig(String name) {
        return targetDbConfigRepository.save(new TargetDbConfig(
                null, name, "jdbc:sqlserver://rollback-test;", "u", "pw", null, null
        )).id();
    }

    @Test
    void createRollsBackTheScheduleRowWhenReplaceForScheduleFails() throws Exception {
        long targetDbConfigId = seedTargetDbConfig("Rollback test target (create)");
        Mockito.doThrow(new RuntimeException("simulated replaceForSchedule failure"))
                .when(scheduleTargetDbConfigRepository).replaceForSchedule(anyLong(), any());

        String body = """
                {
                  "name": "Create rollback test schedule",
                  "enabled": false,
                  "cronExpression": "0 0 9 ? * MON",
                  "timezone": "Asia/Seoul",
                  "spName": "SP_SAMPLE",
                  "targetDbConfigIds": [%d],
                  "actionType": "DISPLAY_ONLY",
                  "actionConfig": {"displayFormat": "TABLE"}
                }
                """.formatted(targetDbConfigId);

        // MockMvc re-throws an exception left unhandled by ApiExceptionHandler (a real
        // deployment's servlet container would turn it into a plain 500 instead) - what N4
        // actually cares about is the DB state below, not the exact HTTP response.
        assertThatThrownBy(() -> mockMvc.perform(post("/api/schedules").contentType(MediaType.APPLICATION_JSON).content(body)))
                .hasRootCauseMessage("simulated replaceForSchedule failure");

        assertThat(scheduleRepository.findAll())
                .noneMatch(schedule -> "Create rollback test schedule".equals(schedule.name()));
    }

    @Test
    void updateRollsBackScheduleChangesWhenReplaceForScheduleFails() throws Exception {
        long targetDbConfigId = seedTargetDbConfig("Rollback test target (update)");
        ScheduleDefinition original = scheduleRepository.save(new ScheduleDefinition(
                null, "Original name before failed update", false, "0 0 9 ? * MON", "Asia/Seoul",
                "SP_SAMPLE", ActionType.DISPLAY_ONLY,
                ActionConfigJson.write(new DisplayOnlyActionConfig(null, null)),
                null, null, null, "tester"
        ));
        scheduleTargetDbConfigRepository.replaceForSchedule(original.id(), java.util.List.of(targetDbConfigId));

        Mockito.doThrow(new RuntimeException("simulated replaceForSchedule failure"))
                .when(scheduleTargetDbConfigRepository).replaceForSchedule(anyLong(), any());

        String body = """
                {
                  "name": "Renamed by failed update",
                  "enabled": true,
                  "cronExpression": "0 30 8 ? * TUE",
                  "timezone": "Asia/Seoul",
                  "spName": "SP_SAMPLE",
                  "targetDbConfigIds": [%d],
                  "actionType": "DISPLAY_ONLY",
                  "actionConfig": {"displayFormat": "TABLE"}
                }
                """.formatted(targetDbConfigId);

        assertThatThrownBy(() -> mockMvc.perform(put("/api/schedules/" + original.id()).contentType(MediaType.APPLICATION_JSON).content(body)))
                .hasRootCauseMessage("simulated replaceForSchedule failure");

        ScheduleDefinition reloaded = scheduleRepository.findById(original.id()).orElseThrow();
        assertThat(reloaded.name()).isEqualTo("Original name before failed update");
        assertThat(reloaded.enabled()).isFalse();
        assertThat(reloaded.cronExpression()).isEqualTo("0 0 9 ? * MON");
    }
}
