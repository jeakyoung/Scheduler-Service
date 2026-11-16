package com.scheduler.engine.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BodyTemplateRendererTest {

    private static final ObjectMapper JSON = new ObjectMapper();

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

    @Test
    void renderJsonEscapesQuotesAndBackslashesInColumnValues() throws Exception {
        // REVIEW.md N1: without escaping, this exact value used to break the JSON
        // structure and inject an unintended "x" field.
        String result = BodyTemplateRenderer.renderJson(
                "{\"title\":\"${EA_TITLE}\"}",
                Map.of("EA_TITLE", "a\",\"x\":\"injected"),
                name -> { throw new AssertionError("should not resolve credentials here"); }
        );

        // The rendered text must itself parse as valid JSON with exactly one field, "title",
        // holding the raw (unescaped-back) value - not a structurally-different document.
        JsonNode parsed = JSON.readTree(result);
        assertThat(parsed.fieldNames()).toIterable().containsExactly("title");
        assertThat(parsed.get("title").asText()).isEqualTo("a\",\"x\":\"injected");
    }

    @Test
    void renderJsonEscapesNewlinesInColumnValues() throws Exception {
        String result = BodyTemplateRenderer.renderJson(
                "{\"body\":\"${EA_TITLE}\"}",
                Map.of("EA_TITLE", "line1\nline2"),
                name -> null
        );

        JsonNode parsed = JSON.readTree(result);
        assertThat(parsed.get("body").asText()).isEqualTo("line1\nline2");
    }

    @Test
    void renderJsonEscapesCredentialValuesToo() throws Exception {
        String result = BodyTemplateRenderer.renderJson(
                "{\"key\":\"${cred:SAMPLE_API_KEY}\"}",
                Map.of(),
                name -> "value-with-\"quotes\"-and-\\backslash"
        );

        JsonNode parsed = JSON.readTree(result);
        assertThat(parsed.get("key").asText()).isEqualTo("value-with-\"quotes\"-and-\\backslash");
    }

    @Test
    void renderWithoutJsonEscapingLeavesHeaderStyleValuesUnescaped() {
        // The plain render() (used for header values, which aren't JSON) must keep its
        // pre-existing unescaped behavior.
        String result = BodyTemplateRenderer.render("${cred:SAMPLE_API_KEY}", Map.of(), name -> "has\"quote");

        assertThat(result).isEqualTo("has\"quote");
    }
}
