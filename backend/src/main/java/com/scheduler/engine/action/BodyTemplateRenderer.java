package com.scheduler.engine.action;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Substitutes `${COLUMN_NAME}` (from the current row) and `${cred:NAME}` (resolved and
 * decrypted on the fly) placeholders in action_config headers/bodyTemplate (PLAN.md §5).
 * credentialResolver results are never cached or logged here - the caller is responsible
 * for not leaking them into summaries/logs either.
 */
public final class BodyTemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{(cred:)?([^}]+)}");
    private static final ObjectMapper JSON = new ObjectMapper();

    private BodyTemplateRenderer() {
    }

    /** Plain substitution, no escaping - for non-JSON contexts like HTTP header values. */
    public static String render(String template, Map<String, Object> row, Function<String, String> credentialResolver) {
        return render(template, row, credentialResolver, false);
    }

    /**
     * Same substitution, but every substituted value is JSON-string-escaped first - for
     * action_config.bodyTemplate, which is JSON text (PLAN.md §5). Without this, a SP column
     * value or credential containing '"', '\', or a newline could break the JSON structure
     * or inject unintended fields into the downstream request body.
     */
    public static String renderJson(String template, Map<String, Object> row, Function<String, String> credentialResolver) {
        return render(template, row, credentialResolver, true);
    }

    private static String render(String template, Map<String, Object> row, Function<String, String> credentialResolver, boolean escapeJson) {
        if (template == null) {
            return null;
        }

        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            boolean isCredential = matcher.group(1) != null;
            String name = matcher.group(2);
            String raw = isCredential ? credentialResolver.apply(name) : stringify(row.get(name));
            String replacement = escapeJson ? escapeJsonString(raw) : (raw == null ? "" : raw);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String stringify(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String escapeJsonString(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        try {
            String quoted = JSON.writeValueAsString(value);
            return quoted.substring(1, quoted.length() - 1);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to JSON-escape template value", e);
        }
    }
}
