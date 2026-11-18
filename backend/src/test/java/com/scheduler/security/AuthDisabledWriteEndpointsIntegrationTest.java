package com.scheduler.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scheduler.domain.config.TargetDbConfig;
import com.scheduler.domain.config.TargetDbConfigRepository;
import com.scheduler.infra.persistence.jdbc.AbstractRepositoryIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reproduces a real bug a user hit: with AUTH_DISABLED=true, SecurityConfig never adds
 * JwtAuthenticationFilter to the chain, so SecurityContextHolder stays empty and the
 * Authentication method-argument resolves to null (not an anonymous/empty Authentication) -
 * ScheduleController/CredentialController were calling authentication.getName() unconditionally
 * to fill the updatedBy audit column, which NPE'd into a 500 on every write. Uses the same
 * dedicated-context pattern as AuthDisabledIntegrationTest (a separate @DynamicPropertySource
 * context from the default-config tests, since the whole point is a different property value).
 */
@AutoConfigureMockMvc
class AuthDisabledWriteEndpointsIntegrationTest extends AbstractRepositoryIntegrationTest {

    @DynamicPropertySource
    static void authDisabledProperty(DynamicPropertyRegistry registry) {
        registry.add("AUTH_DISABLED", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TargetDbConfigRepository targetDbConfigRepository;

    private long targetDbConfigId;

    @BeforeEach
    void seedTargetDbConfig() {
        TargetDbConfig config = targetDbConfigRepository.save(new TargetDbConfig(
                null, "AUTH_DISABLED test target", "jdbc:sqlserver://example-business-db;", "biz_user", "pw", null, null
        ));
        targetDbConfigId = config.id();
    }

    private String scheduleRequestBody(boolean enabled) {
        return """
                {
                  "name": "AUTH_DISABLED schedule",
                  "enabled": %s,
                  "cronExpression": "0 0 9 ? * MON",
                  "timezone": "Asia/Seoul",
                  "spName": "SP_SAMPLE",
                  "targetDbConfigIds": [%d],
                  "actionType": "DISPLAY_ONLY",
                  "actionConfig": {"displayFormat": "TABLE", "columns": null}
                }
                """.formatted(enabled, targetDbConfigId);
    }

    @Test
    void createScheduleWithAuthDisabledSucceedsInsteadOf500() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleRequestBody(false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.updatedBy").value("AUTH_DISABLED"));
    }

    @Test
    void updateScheduleWithAuthDisabledSucceedsInsteadOf500() throws Exception {
        String createResponse = mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleRequestBody(false)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(put("/api/schedules/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleRequestBody(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedBy").value("AUTH_DISABLED"));
    }

    @Test
    void setEnabledWithAuthDisabledSucceedsInsteadOf500() throws Exception {
        String createResponse = mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleRequestBody(false)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(patch("/api/schedules/" + id + "/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled": true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedBy").value("AUTH_DISABLED"));
    }

    @Test
    void createCredentialWithAuthDisabledSucceedsInsteadOf500() throws Exception {
        mockMvc.perform(post("/api/config/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"AUTH_DISABLED_KEY","value":"some-value","description":"test"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.updatedBy").value("AUTH_DISABLED"));
    }

    @Test
    void updateCredentialWithAuthDisabledSucceedsInsteadOf500() throws Exception {
        String createResponse = mockMvc.perform(post("/api/config/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"AUTH_DISABLED_KEY_2","value":"some-value","description":"test"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(put("/api/config/credentials/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"AUTH_DISABLED_KEY_2","value":"new-value","description":"updated"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedBy").value("AUTH_DISABLED"));
    }

    /**
     * Unlike updatedBy (an audit field, fine with a placeholder), "who am I" has no meaningful
     * answer when Authentication is null - must reject cleanly (401), not NPE into a 500.
     */
    @Test
    void meWithAuthDisabledReturns401InsteadOf500() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordWithAuthDisabledReturns401InsteadOf500() throws Exception {
        mockMvc.perform(put("/api/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"whatever","newPassword":"NewPassw0rd!123"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
