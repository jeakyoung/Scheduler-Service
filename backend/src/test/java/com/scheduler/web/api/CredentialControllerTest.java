package com.scheduler.web.api;

import com.scheduler.domain.config.Credential;
import com.scheduler.domain.config.CredentialRepository;
import com.scheduler.domain.config.TargetDbConfig;
import com.scheduler.domain.config.TargetDbConfigRepository;
import com.scheduler.domain.schedule.ActionConfigJson;
import com.scheduler.domain.schedule.ActionType;
import com.scheduler.domain.schedule.ApiCallActionConfig;
import com.scheduler.domain.schedule.GroupingMode;
import com.scheduler.domain.schedule.ScheduleDefinition;
import com.scheduler.domain.schedule.ScheduleRepository;
import com.scheduler.infra.persistence.jdbc.AbstractRepositoryIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@WithMockUser(username = "test-admin", roles = "ADMIN")
class CredentialControllerTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CredentialRepository credentialRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private TargetDbConfigRepository targetDbConfigRepository;

    private long seedTargetDbConfig() {
        return targetDbConfigRepository.save(new TargetDbConfig(
                null, "Test target DB", "jdbc:sqlserver://x;", "u", "pw", null, null
        )).id();
    }

    @Test
    void createReturnsMaskedValueNeverPlaintext() throws Exception {
        String response = mockMvc.perform(post("/api/config/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"SAMPLE_KEY","value":"super-secret-api-key","description":"test"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maskedValue").value(org.hamcrest.Matchers.endsWith("-key")))
                .andExpect(jsonPath("$.maskedValue").value(org.hamcrest.Matchers.startsWith("*")))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("super-secret-api-key");
    }

    @Test
    void listNeverExposesPlaintextValue() throws Exception {
        credentialRepository.save(new Credential(null, "ANOTHER_KEY", "another-secret-value", null, null, null));

        String response = mockMvc.perform(get("/api/config/credentials"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("another-secret-value");
    }

    @Test
    void deleteWithoutForceIsBlockedWhenReferencedInActionConfig() throws Exception {
        Credential credential = credentialRepository.save(new Credential(null, "REFERENCED_KEY", "secret", null, null, null));
        String actionConfig = ActionConfigJson.write(new ApiCallActionConfig(
                "POST", "https://notify.example.com/api/push",
                Map.of("X-Api-Key", "${cred:REFERENCED_KEY}"),
                GroupingMode.PER_ROW, null, "{}"
        ));
        scheduleRepository.save(new ScheduleDefinition(
                null, "Uses credential", false, "0 0 9 ? * MON", "Asia/Seoul",
                "SP_X", seedTargetDbConfig(), ActionType.API_CALL, actionConfig, null, null, null, "tester"
        ));

        mockMvc.perform(delete("/api/config/credentials/" + credential.id()))
                .andExpect(status().isConflict());

        assertThat(credentialRepository.findById(credential.id())).isPresent();
    }

    @Test
    void deleteWithForceRemovesEvenWhenReferenced() throws Exception {
        Credential credential = credentialRepository.save(new Credential(null, "FORCE_DELETE_KEY", "secret", null, null, null));
        String actionConfig = ActionConfigJson.write(new ApiCallActionConfig(
                "POST", "https://notify.example.com/api/push",
                Map.of("X-Api-Key", "${cred:FORCE_DELETE_KEY}"),
                GroupingMode.PER_ROW, null, "{}"
        ));
        scheduleRepository.save(new ScheduleDefinition(
                null, "Uses credential 2", false, "0 0 9 ? * MON", "Asia/Seoul",
                "SP_X", seedTargetDbConfig(), ActionType.API_CALL, actionConfig, null, null, null, "tester"
        ));

        mockMvc.perform(delete("/api/config/credentials/" + credential.id()).param("force", "true"))
                .andExpect(status().isNoContent());

        assertThat(credentialRepository.findById(credential.id())).isEmpty();
    }

    @Test
    void deleteSucceedsWhenUnreferenced() throws Exception {
        Credential credential = credentialRepository.save(new Credential(null, "UNUSED_KEY", "secret", null, null, null));

        mockMvc.perform(delete("/api/config/credentials/" + credential.id())).andExpect(status().isNoContent());

        assertThat(credentialRepository.findById(credential.id())).isEmpty();
    }
}
