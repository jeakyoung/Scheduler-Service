package com.scheduler.web.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scheduler.domain.config.TargetDbConfig;
import com.scheduler.domain.config.TargetDbConfigRepository;
import com.scheduler.domain.schedule.ActionConfigJson;
import com.scheduler.domain.schedule.ActionType;
import com.scheduler.domain.schedule.DisplayOnlyActionConfig;
import com.scheduler.domain.schedule.ScheduleDefinition;
import com.scheduler.domain.schedule.ScheduleRepository;
import com.scheduler.infra.persistence.jdbc.AbstractRepositoryIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@WithMockUser(username = "test-admin", roles = "ADMIN")
class TargetDbConfigControllerTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TargetDbConfigRepository targetDbConfigRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Test
    void createReturnsMaskedPasswordNeverPlaintext() throws Exception {
        String response = mockMvc.perform(post("/api/config/target-db")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Test ERP","jdbcUrl":"jdbc:sqlserver://biz-db;","username":"erp_user","password":"super-secret-pw"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maskedPassword").value(org.hamcrest.Matchers.endsWith("t-pw")))
                .andExpect(jsonPath("$.maskedPassword").value(org.hamcrest.Matchers.startsWith("*")))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("super-secret-pw");
    }

    @Test
    void listNeverExposesPlaintextPassword() throws Exception {
        targetDbConfigRepository.save(new TargetDbConfig(null, "A", "jdbc:x;", "u", "another-secret-pw", null, null));

        String response = mockMvc.perform(get("/api/config/target-db"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("another-secret-pw");
    }

    @Test
    void updateReplacesFields() throws Exception {
        TargetDbConfig saved = targetDbConfigRepository.save(new TargetDbConfig(null, "Old", "jdbc:old;", "old_user", "old-pw", null, null));

        mockMvc.perform(put("/api/config/target-db/" + saved.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"New","jdbcUrl":"jdbc:new;","username":"new_user","password":"new-secret-pw"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New"))
                .andExpect(jsonPath("$.username").value("new_user"));
    }

    @Test
    void deleteIsBlockedWhenReferencedBySchedule() throws Exception {
        TargetDbConfig config = targetDbConfigRepository.save(new TargetDbConfig(null, "In use", "jdbc:x;", "u", "pw", null, null));
        scheduleRepository.save(new ScheduleDefinition(
                null, "Referencing schedule", false, "0 0 9 ? * MON", "Asia/Seoul",
                "SP_X", config.id(), ActionType.DISPLAY_ONLY,
                ActionConfigJson.write(new DisplayOnlyActionConfig(null, null)),
                null, null, null, "tester"
        ));

        mockMvc.perform(delete("/api/config/target-db/" + config.id()))
                .andExpect(status().isConflict());

        assertThat(targetDbConfigRepository.findById(config.id())).isPresent();
    }

    @Test
    void deleteSucceedsWhenUnreferenced() throws Exception {
        TargetDbConfig config = targetDbConfigRepository.save(new TargetDbConfig(null, "Unused", "jdbc:x;", "u", "pw", null, null));

        mockMvc.perform(delete("/api/config/target-db/" + config.id())).andExpect(status().isNoContent());

        assertThat(targetDbConfigRepository.findById(config.id())).isEmpty();
    }
}
