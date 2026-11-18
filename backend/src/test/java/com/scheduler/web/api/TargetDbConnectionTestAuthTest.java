package com.scheduler.web.api;

import com.scheduler.infra.persistence.jdbc.AbstractRepositoryIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PLAN.md §6 (2026-11-18)/TASKS.md T28 DoD - both test-connection endpoints must stay inside
 * the existing /api/** auth boundary without any SecurityConfig change. Pins
 * app.auth.disabled=false the same way AuthControllerTest does, so a developer/CI box with
 * AUTH_DISABLED=true already exported can't silently turn these into false negatives.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.auth.disabled=false")
class TargetDbConnectionTestAuthTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unsavedInputTestConnectionRequiresAuth() throws Exception {
        mockMvc.perform(post("/api/config/target-db/test-connection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jdbcUrl":"jdbc:sqlserver://x;","username":"u","password":"p"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void savedConfigTestConnectionRequiresAuth() throws Exception {
        mockMvc.perform(post("/api/config/target-db/1/test-connection"))
                .andExpect(status().isUnauthorized());
    }
}
