package com.scheduler.engine.action;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BodyTemplateRendererTest {

    @Test
    void substitutesColumnPlaceholders() {
        String result = BodyTemplateRenderer.render(
                "{\"empList\":[\"${EMPLOYEE_NO}\"],\"title\":\"${EA_TITLE}\"}",
                Map.of("EMPLOYEE_NO", "E001", "EA_TITLE", "Sample Title"),
                name -> { throw new AssertionError("should not resolve credentials here"); }
        );

        assertThat(result).isEqualTo("{\"empList\":[\"E001\"],\"title\":\"Sample Title\"}");
    }

    @Test
    void substitutesCredentialPlaceholders() {
        String result = BodyTemplateRenderer.render(
                "X-Api-Key: ${cred:SAMPLE_API_KEY}",
                Map.of(),
                name -> name.equals("SAMPLE_API_KEY") ? "decrypted-value" : null
        );

        assertThat(result).isEqualTo("X-Api-Key: decrypted-value");
    }

    @Test
    void missingColumnValueBecomesEmptyString() {
        String result = BodyTemplateRenderer.render("${MISSING}", Map.of(), name -> null);

        assertThat(result).isEmpty();
    }

    @Test
    void nullTemplateReturnsNull() {
        assertThat(BodyTemplateRenderer.render(null, Map.of(), name -> null)).isNull();
    }
}
