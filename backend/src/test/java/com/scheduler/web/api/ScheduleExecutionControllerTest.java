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
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

    /**
     * T21 (TASKS.md) - multi-target-DB API_CALL scenario, end-to-end through the real
     * run-now REST endpoint (not a ScheduleExecutor-level unit test like T17's). Both target
     * DBs point at the same shared Testcontainers MSSQL instance (same convention as the rest
     * of this class) since SP_TEST_SAMPLE only needs to exist once - sequential visiting order
     * is schedule_target_db_config's ORDER BY target_db_config_id (T16), so the first-created
     * config's single HTTP call lands first (200), the second-created config's lands second
     * (500), deterministically producing one success and one failure.
     */
    @Test
    void runNowWithTwoTargetDbsAndApiCallOneFailureYieldsPartialSuccess() throws Exception {
        List<String> receivedBodies = new CopyOnWriteArrayList<>();
        AtomicInteger callCount = new AtomicInteger();
        int[] responseStatusesInOrder = {200, 500};

        HttpServer httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        httpServer.createContext("/push", exchange -> {
            receivedBodies.add(new String(exchange.getRequestBody().readAllBytes()));
            int index = Math.min(callCount.getAndIncrement(), responseStatusesInOrder.length - 1);
            byte[] response = "{}".getBytes();
            exchange.sendResponseHeaders(responseStatusesInOrder[index], response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        httpServer.start();
        try {
            String pushUrl = "http://localhost:" + httpServer.getAddress().getPort() + "/push";

            TargetDbConfig secondConfig = targetDbConfigRepository.save(new TargetDbConfig(
                    null, "Second same-container target DB",
                    MSSQL.getJdbcUrl() + ";trustServerCertificate=true;",
                    MSSQL.getUsername(), MSSQL.getPassword(), null, null
            ));

            String body = """
                    {
                      "name": "Multi target API_CALL schedule",
                      "enabled": false,
                      "cronExpression": "0 0 9 ? * MON",
                      "timezone": "Asia/Seoul",
                      "spName": "SP_TEST_SAMPLE",
                      "targetDbConfigIds": [%d, %d],
                      "actionType": "API_CALL",
                      "actionConfig": {
                        "method": "POST",
                        "url": "%s",
                        "headers": {},
                        "groupingMode": "PER_ROW",
                        "bodyTemplate": "{\\"empList\\":[\\"${EMPLOYEE_NO}\\"]}"
                      }
                    }
                    """.formatted(targetDbConfigId, secondConfig.id(), pushUrl);

            String createResponse = mockMvc.perform(post("/api/schedules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            long id = objectMapper.readTree(createResponse).get("id").asLong();

            String runNowResponse = mockMvc.perform(post("/api/schedules/" + id + "/run-now"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            JsonNode node = objectMapper.readTree(runNowResponse);
            assertThat(node.at("/result/status").asText()).isEqualTo("PARTIAL_SUCCESS");
            assertThat(node.at("/result/targetDbResults")).hasSize(2);
            assertThat(node.at("/result/targetDbResults/0/targetDbConfigId").asLong()).isEqualTo(targetDbConfigId);
            assertThat(node.at("/result/targetDbResults/0/status").asText()).isEqualTo("SUCCESS");
            assertThat(node.at("/result/targetDbResults/1/targetDbConfigId").asLong()).isEqualTo(secondConfig.id());
            assertThat(node.at("/result/targetDbResults/1/status").asText()).isEqualTo("FAILURE");
            assertThat(node.at("/result/targetDbResults/1/errorMessage").asText()).contains("HTTP 500");
            assertThat(receivedBodies).hasSize(2);
        } finally {
            httpServer.stop(0);
        }
    }
}
