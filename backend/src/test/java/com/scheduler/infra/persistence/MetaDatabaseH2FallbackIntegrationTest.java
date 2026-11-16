package com.scheduler.infra.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the DB_URL-unset local-dev fallback (application.yml defaults spring.datasource.url
 * to H2 when DB_URL isn't set) - everything else in the suite deliberately pins DB_URL to a
 * real SQL Server Testcontainer (see AbstractQuartzIntegrationTest/AbstractRepositoryIntegrationTest)
 * and must keep doing so unaffected by this class.
 *
 * DB_URL is pointed at an in-memory H2 instance here rather than left genuinely unset, since
 * that's the only way to deterministically exercise the H2 vendor branch in a JVM-level test
 * regardless of whatever happens to be in the host's own environment when `mvn test` runs - an
 * in-memory URL still starts with "jdbc:h2:", so it drives the exact same Flyway
 * classpath:db/migration/h2 location and QuartzConfig driverDelegateClass branch as the real
 * file-based fallback default.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "test-admin", roles = "ADMIN")
class MetaDatabaseH2FallbackIntegrationTest {

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        String schema = "h2fallback" + UUID.randomUUID().toString().replace("-", "");
        registry.add("DB_URL", () -> "jdbc:h2:mem:" + schema + ";DB_CLOSE_DELAY=-1");
        registry.add("MASTER_ENCRYPTION_KEY", () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        registry.add("JWT_SIGNING_KEY", () -> "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Scheduler scheduler;

    @Test
    void quartzSchedulerStartsAgainstH2MetaDb() throws Exception {
        assertThat(scheduler.isStarted()).isTrue();
    }

    @Test
    void scheduleCreationApiWorksAgainstH2FallbackMetaDb() throws Exception {
        String targetDbConfigResponse = mockMvc.perform(post("/api/config/target-db")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "H2 fallback test target",
                                  "jdbcUrl": "jdbc:sqlserver://example-business-db;",
                                  "username": "biz_user",
                                  "password": "biz_pw"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long targetDbConfigId = objectMapper.readTree(targetDbConfigResponse).get("id").asLong();

        String scheduleResponse = mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "H2 fallback test schedule",
                                  "enabled": true,
                                  "cronExpression": "0 0 9 ? * MON",
                                  "timezone": "Asia/Seoul",
                                  "spName": "SP_SAMPLE",
                                  "targetDbConfigIds": [%d],
                                  "actionType": "DISPLAY_ONLY",
                                  "actionConfig": {"displayFormat": "TABLE"}
                                }
                                """.formatted(targetDbConfigId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.nextFireTime").value(notNullValue()))
                .andReturn().getResponse().getContentAsString();

        long scheduleId = objectMapper.readTree(scheduleResponse).get("id").asLong();
        assertThat(scheduler.checkExists(JobKey.jobKey("schedule-" + scheduleId))).isTrue();
    }
}
