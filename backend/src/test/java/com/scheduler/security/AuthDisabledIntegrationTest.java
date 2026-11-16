package com.scheduler.security;

import com.scheduler.infra.persistence.jdbc.AbstractRepositoryIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AUTH_DISABLED is a local-dev-only opt-in escape hatch (default false, i.e. normal auth
 * stays on unless someone explicitly flips it) - this gets its own Spring context (via the
 * extra @DynamicPropertySource below, merged with the inherited DB/key properties) rather
 * than sharing the default-config context the other web/api tests use, since the whole
 * point is exercising a different value of this property.
 */
@AutoConfigureMockMvc
class AuthDisabledIntegrationTest extends AbstractRepositoryIntegrationTest {

    @DynamicPropertySource
    static void authDisabledProperty(DynamicPropertyRegistry registry) {
        registry.add("AUTH_DISABLED", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void protectedEndpointIsReachableWithoutATokenWhenAuthIsDisabled() throws Exception {
        mockMvc.perform(get("/api/schedules")).andExpect(status().isOk());
    }
}
