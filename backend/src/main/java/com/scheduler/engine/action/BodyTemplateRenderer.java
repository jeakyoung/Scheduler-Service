package com.scheduler.engine.action;

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

    private BodyTemplateRenderer() {
    }

    public static String render(String template, Map<String, Object> row, Function<String, String> credentialResolver) {
        if (template == null) {
            return null;
        }

        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            boolean isCredential = matcher.group(1) != null;
            String name = matcher.group(2);
            String replacement = isCredential
                    ? credentialResolver.apply(name)
                    : stringify(row.get(name));
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement == null ? "" : replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String stringify(Object value) {
        return value == null ? "" : value.toString();
    }
}
