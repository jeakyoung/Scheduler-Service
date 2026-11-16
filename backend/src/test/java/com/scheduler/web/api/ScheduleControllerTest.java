package com.scheduler.web.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scheduler.domain.config.TargetDbConfig;
import com.scheduler.domain.config.TargetDbConfigRepository;
import com.scheduler.engine.quartz.AbstractQuartzIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@WithMockUser(username = "test-admin", roles = "ADMIN")
class ScheduleControllerTest extends AbstractQuartzIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TargetDbConfigRepository targetDbConfigRepository;

    @Autowired
    private Scheduler scheduler;

    private long targetDbConfigId;

    @BeforeEach
    void seedTargetDbConfig() {
        TargetDbConfig config = targetDbConfigRepository.save(new TargetDbConfig(
                null, "Test Target DB", "jdbc:sqlserver://example-business-db;", "biz_user", "pw", null, null
        ));
        targetDbConfigId = config.id();
    }

    private String apiCallRequestBody(boolean enabled, String cron) {
        return """
                {
                  "name": "Sample schedule",
                  "enabled": %s,
                  "cronExpression": "%s",
                  "timezone": "Asia/Seoul",
                  "spName": "SP_SAMPLE_NOTICE_LIST",
                  "targetDbConfigIds": [%d],
                  "actionType": "API_CALL",
                  "actionConfig": {
                    "method": "POST",
                    "url": "https://notify.example.com/api/push",
                    "headers": {"X-Api-Key": "${cred:SAMPLE_API_KEY}"},
                    "groupingMode": "PER_ROW",
                    "bodyTemplate": "{\\"empList\\":[\\"${EMPLOYEE_NO}\\"]}"
                  },
                  "description": "sample"
                }
                """.formatted(enabled, cron, targetDbConfigId);
    }

    @Test
    void createReturnsCreatedAndRegistersQuartzTrigger() throws Exception {
        String response = mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(apiCallRequestBody(true, "0 0/30 * ? * MON-FRI")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.nextFireTime").value(notNullValue()))
                .andReturn().getResponse().getContentAsString();

        long id = objectMapper.readTree(response).get("id").asLong();
        assertThat(scheduler.checkExists(JobKey.jobKey("schedule-" + id))).isTrue();
    }

    @Test
    void createWithMaliciousSpNameIsBadRequest() throws Exception {
        String body = """
                {
                  "name": "Injection attempt",
                  "enabled": true,
                  "cronExpression": "0 0 9 ? * MON",
                  "timezone": "Asia/Seoul",
                  "spName": "sp_test; DROP TABLE some_table; --",
                  "targetDbConfigIds": [%d],
                  "actionType": "DISPLAY_ONLY",
                  "actionConfig": {"displayFormat": "TABLE"}
                }
                """.formatted(targetDbConfigId);

        mockMvc.perform(post("/api/schedules").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithMissingUrlForApiCallIsBadRequest() throws Exception {
        String body = """
                {
                  "name": "Bad schedule",
                  "enabled": true,
                  "cronExpression": "0 0 9 ? * MON",
                  "timezone": "Asia/Seoul",
                  "spName": "SP_SAMPLE",
                  "targetDbConfigIds": [%d],
                  "actionType": "API_CALL",
                  "actionConfig": {"method": "POST", "groupingMode": "PER_ROW"}
                }
                """.formatted(targetDbConfigId);

        mockMvc.perform(post("/api/schedules").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithInvalidCronIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(apiCallRequestBody(true, "not a cron")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithEmptyTargetDbConfigIdsIsBadRequest() throws Exception {
        String body = """
                {
                  "name": "No target DB",
                  "enabled": true,
                  "cronExpression": "0 0 9 ? * MON",
                  "timezone": "Asia/Seoul",
                  "spName": "SP_SAMPLE",
                  "targetDbConfigIds": [],
                  "actionType": "DISPLAY_ONLY",
                  "actionConfig": {"displayFormat": "TABLE"}
                }
                """;

        mockMvc.perform(post("/api/schedules").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithNonExistentTargetDbConfigIdIsBadRequest() throws Exception {
        long nonExistentId = targetDbConfigId + 999_999L;
        String body = """
                {
                  "name": "Bad target DB reference",
                  "enabled": true,
                  "cronExpression": "0 0 9 ? * MON",
                  "timezone": "Asia/Seoul",
                  "spName": "SP_SAMPLE",
                  "targetDbConfigIds": [%d],
                  "actionType": "DISPLAY_ONLY",
                  "actionConfig": {"displayFormat": "TABLE"}
                }
                """.formatted(nonExistentId);

        mockMvc.perform(post("/api/schedules").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAndListResolveTargetDbConfigNamesForMultipleTargets() throws Exception {
        TargetDbConfig secondConfig = targetDbConfigRepository.save(new TargetDbConfig(
                null, "Second Target DB", "jdbc:sqlserver://second-business-db;", "biz_user2", "pw", null, null
        ));
        String body = """
                {
                  "name": "Multi target schedule",
                  "enabled": true,
                  "cronExpression": "0 0 9 ? * MON",
                  "timezone": "Asia/Seoul",
                  "spName": "SP_SAMPLE",
                  "targetDbConfigIds": [%d, %d],
                  "actionType": "DISPLAY_ONLY",
                  "actionConfig": {"displayFormat": "TABLE"}
                }
                """.formatted(targetDbConfigId, secondConfig.id());

        String createResponse = mockMvc.perform(post("/api/schedules").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetDbConfigIds", org.hamcrest.Matchers.containsInAnyOrder(
                        (int) targetDbConfigId, secondConfig.id().intValue())))
                .andExpect(jsonPath("$.targetDbConfigs[*].name", org.hamcrest.Matchers.containsInAnyOrder(
                        "Test Target DB", "Second Target DB")))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(createResponse).get("id").asLong();

        // The shared singleton test DB accumulates schedules across test methods, so look up
        // this schedule specifically rather than assuming list order.
        String listResponse = mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode listNode = objectMapper.readTree(listResponse);
        JsonNode thisSchedule = null;
        for (JsonNode node : listNode) {
            if (node.get("id").asLong() == id) {
                thisSchedule = node;
                break;
            }
        }
        assertThat(thisSchedule).isNotNull();
        List<String> names = new ArrayList<>();
        thisSchedule.get("targetDbConfigs").forEach(ref -> names.add(ref.get("name").asText()));
        assertThat(names).containsExactlyInAnyOrder("Test Target DB", "Second Target DB");
    }

    @Test
    void getReturnsScheduleWithEmbeddedParameters() throws Exception {
        long id = createSchedule(true, "0 0 9 ? * MON");

        mockMvc.perform(put("/api/schedules/" + id + "/parameters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"paramOrder":1,"paramName":"@FromDate","dataType":"DATE","valueSource":"CURRENT_DATE"}]
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedules/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parameters", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.parameters[0].paramName").value("@FromDate"));
    }

    @Test
    void updateWithNewCronChangesQuartzNextFireTime() throws Exception {
        long id = createSchedule(true, "0 0 9 ? * MON");
        var firstTrigger = scheduler.getTrigger(org.quartz.TriggerKey.triggerKey("schedule-" + id + "-trigger"));
        var firstNextFire = firstTrigger.getNextFireTime();

        mockMvc.perform(put("/api/schedules/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(apiCallRequestBody(true, "0 30 8 ? * TUE")))
                .andExpect(status().isOk());

        var updatedTrigger = scheduler.getTrigger(org.quartz.TriggerKey.triggerKey("schedule-" + id + "-trigger"));
        assertThat(updatedTrigger.getNextFireTime()).isNotEqualTo(firstNextFire);
    }

    @Test
    void togglingEnabledAddsAndRemovesQuartzTrigger() throws Exception {
        long id = createSchedule(true, "0 0 9 ? * MON");
        assertThat(scheduler.checkExists(JobKey.jobKey("schedule-" + id))).isTrue();

        mockMvc.perform(patch("/api/schedules/" + id + "/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isOk());
        assertThat(scheduler.checkExists(JobKey.jobKey("schedule-" + id))).isFalse();

        mockMvc.perform(patch("/api/schedules/" + id + "/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": true}"))
                .andExpect(status().isOk());
        assertThat(scheduler.checkExists(JobKey.jobKey("schedule-" + id))).isTrue();
    }

    @Test
    void deleteRemovesScheduleAndQuartzJob() throws Exception {
        long id = createSchedule(true, "0 0 9 ? * MON");

        mockMvc.perform(delete("/api/schedules/" + id)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/schedules/" + id)).andExpect(status().isNotFound());
        assertThat(scheduler.checkExists(JobKey.jobKey("schedule-" + id))).isFalse();
    }

    @Test
    void replaceParametersWithInvalidLiteralIsBadRequest() throws Exception {
        long id = createSchedule(true, "0 0 9 ? * MON");

        mockMvc.perform(put("/api/schedules/" + id + "/parameters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"paramOrder":1,"paramName":"@Count","dataType":"INT","valueSource":"LITERAL","literalValue":"not-a-number"}]
                                """))
                .andExpect(status().isBadRequest());
    }

    private long createSchedule(boolean enabled, String cron) throws Exception {
        String response = mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(apiCallRequestBody(enabled, cron)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(response);
        return node.get("id").asLong();
    }
}
