package com.scheduler.web.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scheduler.domain.config.TargetDbConfig;
import com.scheduler.domain.config.TargetDbConfigRepository;
import com.scheduler.engine.quartz.AbstractQuartzIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import com.scheduler.security.JwtTokenService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Points target_db_config at the SAME Testcontainers MSSQL instance backing the meta DB, with
 * real throwaway stored procedures created for the test - so run-now exercises the actual
 * JDBC EXEC round-trip (StoredProcedureInvoker -> real SQL Server), not a mocked one.
 */
@AutoConfigureMockMvc
@WithMockUser(username = "test-admin", roles = "ADMIN")
class ScheduleExecutionControllerTest extends AbstractQuartzIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TargetDbConfigRepository targetDbConfigRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenService jwtTokenService;

    private long targetDbConfigId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                CREATE OR ALTER PROCEDURE SP_TEST_SAMPLE AS
                BEGIN
                  SELECT 'E001' AS EMPLOYEE_NO, 'Title A' AS EA_TITLE
                END
                """);
        jdbcTemplate.execute("""
                CREATE OR ALTER PROCEDURE SP_TEST_SLOW AS
                BEGIN
                  WAITFOR DELAY '00:00:03'
                  SELECT 'X' AS COL
                END
                """);

        TargetDbConfig config = targetDbConfigRepository.save(new TargetDbConfig(
                null, "Same-container target DB",
                MSSQL.getJdbcUrl() + ";trustServerCertificate=true;",
                MSSQL.getUsername(), MSSQL.getPassword(), null, null
        ));
        targetDbConfigId = config.id();
    }

    private long createDisplayOnlySchedule(String spName) throws Exception {
        String body = """
                {
                  "name": "Run-now test schedule",
                  "enabled": false,
                  "cronExpression": "0 0 9 ? * MON",
                  "timezone": "Asia/Seoul",
                  "spName": "%s",
                  "targetDbConfigIds": [%d],
                  "actionType": "DISPLAY_ONLY",
                  "actionConfig": {"displayFormat": "TABLE"}
                }
                """.formatted(spName, targetDbConfigId);

        String response = mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    void runNowExecutesImmediatelyAndReturnsResultSynchronously() throws Exception {
        long id = createDisplayOnlySchedule("SP_TEST_SAMPLE");

        String response = mockMvc.perform(post("/api/schedules/" + id + "/run-now"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode node = objectMapper.readTree(response);
        assertThat(node.at("/result/status").asText()).isEqualTo("SUCCESS");
        assertThat(node.at("/result/triggerType").asText()).isEqualTo("MANUAL");
        // PLAN.md §6/T18 - run-now must expose the per-target-DB breakdown, not just the
        // top-level summary.
        assertThat(node.at("/result/targetDbResults")).hasSize(1);
        assertThat(node.at("/result/targetDbResults/0/status").asText()).isEqualTo("SUCCESS");
        assertThat(node.at("/result/targetDbResults/0/targetDbConfigId").asLong()).isEqualTo(targetDbConfigId);
    }

    @Test
    void runNowOnDisplayOnlyScheduleReturnsProcessedDisplayOutput() throws Exception {
        long id = createDisplayOnlySchedule("SP_TEST_SAMPLE");

        String response = mockMvc.perform(post("/api/schedules/" + id + "/run-now"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode node = objectMapper.readTree(response);
        assertThat(node.get("displayOutputs")).hasSize(1);
        assertThat(node.at("/displayOutputs/0/rows/0/EMPLOYEE_NO").asText()).isEqualTo("E001");
        assertThat(node.at("/displayOutputs/0/rows/0/EA_TITLE").asText()).isEqualTo("Title A");
    }

    @Test
    void lastResultReflectsTheMostRecentRunNow() throws Exception {
        long id = createDisplayOnlySchedule("SP_TEST_SAMPLE");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/schedules/" + id + "/last-result"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/schedules/" + id + "/run-now")).andExpect(status().isOk());

        String response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/schedules/" + id + "/last-result"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(response).at("/result/status").asText()).isEqualTo("SUCCESS");
    }

    @Test
    void failedRunNowDoesNotReturnStaleDisplayOutputFromPriorSuccess() throws Exception {
        // REVIEW.md N3: DisplayOutputStore only ever holds the latest *successful* result
        // and is never cleared on a later failure, so without the fix a failed run's
        // response would still carry this earlier success's rows.
        long id = createDisplayOnlySchedule("SP_TEST_SAMPLE");
        mockMvc.perform(post("/api/schedules/" + id + "/run-now"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.result.status").value("SUCCESS"));

        jdbcTemplate.execute("DROP PROCEDURE SP_TEST_SAMPLE");

        String response = mockMvc.perform(post("/api/schedules/" + id + "/run-now"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode node = objectMapper.readTree(response);
        assertThat(node.at("/result/status").asText()).isEqualTo("FAILURE");
        assertThat(node.get("displayOutputs")).isEmpty();

        String lastResultResponse = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/schedules/" + id + "/last-result"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode lastResultNode = objectMapper.readTree(lastResultResponse);
        assertThat(lastResultNode.at("/result/status").asText()).isEqualTo("FAILURE");
        assertThat(lastResultNode.get("displayOutputs")).isEmpty();
    }

    @Test
    void concurrentRunNowIsRejectedWithConflict() throws Exception {
        long id = createDisplayOnlySchedule("SP_TEST_SLOW");

        // @WithMockUser's security context is thread-local and does NOT propagate to a
        // manually-spawned background Thread, so the first call must carry a real
        // Authorization header instead - otherwise it 401s immediately instead of actually
        // blocking on the slow SP, and the "concurrent" second call never collides with it.
        String token = jwtTokenService.generate("concurrency-test-user", "ADMIN");
        MockHttpServletRequestBuilder runNow = post("/api/schedules/" + id + "/run-now")
                .header("Authorization", "Bearer " + token);

        CountDownLatch firstStarted = new CountDownLatch(1);
        AtomicReference<Exception> backgroundError = new AtomicReference<>();
        Thread firstRun = new Thread(() -> {
            try {
                firstStarted.countDown();
                mockMvc.perform(runNow).andExpect(status().isOk());
            } catch (Exception e) {
                backgroundError.set(e);
            }
        });
        firstRun.start();
        firstStarted.await();
        Thread.sleep(500); // give the WAITFOR DELAY call time to actually start

        mockMvc.perform(runNow).andExpect(status().isConflict());

        firstRun.join(TimeUnit.SECONDS.toMillis(10));
        assertThat(firstRun.isAlive()).isFalse();
        assertThat(backgroundError.get()).isNull();
    }
}
