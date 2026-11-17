package com.scheduler.web;

import com.scheduler.infra.persistence.jdbc.AbstractRepositoryIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PLAN.md §11.3/TASKS.md T24 DoD. Uses the src/test/resources/static fixture
 * (index.html + assets/app.js) rather than the real npm-built frontend/dist output - the
 * latter is only materialized by backend/pom.xml's frontend-maven-plugin at the
 * prepare-package phase (T23), which `mvn test` never reaches, so this test must not depend
 * on it being present.
 *
 * Auth stays at its default (enabled) in this test - that's what makes case 3 below a real
 * check of SpaWebConfig's "/api/** is implicitly excluded by HandlerMapping priority"
 * assumption, rather than a tautology.
 */
@AutoConfigureMockMvc
class SpaFallbackIntegrationTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unknownExtensionlessPathFallsBackToIndexHtml() throws Exception {
        mockMvc.perform(get("/schedules"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("SPA_FALLBACK_TEST_FIXTURE")));
    }

    @Test
    void realStaticAssetIsServedAsIsNotReplacedByIndexHtml() throws Exception {
        mockMvc.perform(get("/assets/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("SPA_FALLBACK_TEST_FIXTURE_ASSET")));
    }

    @Test
    void unauthenticatedApiRequestIsRejectedNotFallenBackToIndexHtml() throws Exception {
        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isUnauthorized());
    }
}
