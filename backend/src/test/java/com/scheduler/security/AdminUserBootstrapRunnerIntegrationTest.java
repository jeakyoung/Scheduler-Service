package com.scheduler.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scheduler.domain.admin.AdminUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MSSQLServerContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deliberately uses its OWN fresh container rather than the shared singleton ones used
 * elsewhere (AbstractRepositoryIntegrationTest / AbstractQuartzIntegrationTest): those are
 * shared across many test classes in the same JVM, so by the time this test runs,
 * admin_user may already have rows inserted by other tests - which would make
 * AdminUserBootstrapRunner's "only seed if no admin exists yet" precondition unreliable to
 * assert on. A guaranteed-empty schema is the whole point of this test.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminUserBootstrapRunnerIntegrationTest {

    private static final MSSQLServerContainer<?> MSSQL = new MSSQLServerContainer<>(
            "mcr.microsoft.com/mssql/server:2022-latest")
            .acceptLicense();

    static {
        MSSQL.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", () -> MSSQL.getJdbcUrl() + ";trustServerCertificate=true;");
        registry.add("DB_USERNAME", MSSQL::getUsername);
        registry.add("DB_PASSWORD", MSSQL::getPassword);
        registry.add("MASTER_ENCRYPTION_KEY", () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        registry.add("JWT_SIGNING_KEY", () -> "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=");
        registry.add("spring.quartz.auto-startup", () -> "false");
        registry.add("INITIAL_ADMIN_USERNAME", () -> "bootstrap-admin");
        registry.add("INITIAL_ADMIN_PASSWORD", () -> "bootstrap-password-123");
    }

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void seedsExactlyOneAdminFromEnvVarsAndItCanLogIn() throws Exception {
        assertThat(adminUserRepository.findAll()).hasSize(1);
        assertThat(adminUserRepository.findByUsername("bootstrap-admin")).isPresent();

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"bootstrap-admin","password":"bootstrap-password-123"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode node = objectMapper.readTree(response);
        assertThat(node.get("token").asText()).isNotBlank();
    }
}
