package com.scheduler.domain.schedule;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON <-> Java mapping for schedule_definition.action_config (PLAN.md §5). The column
 * shape depends on action_type, so callers must know which one to parse into.
 */
public final class ActionConfigJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ActionConfigJson() {
    }

    public static String write(ApiCallActionConfig config) {
        return write((Object) config);
    }

    public static String write(DisplayOnlyActionConfig config) {
        return write((Object) config);
    }

    public static ApiCallActionConfig readApiCall(String json) {
        return read(json, ApiCallActionConfig.class);
    }

    public static DisplayOnlyActionConfig readDisplayOnly(String json) {
        return read(json, DisplayOnlyActionConfig.class);
    }

    private static String write(Object config) {
        try {
            return MAPPER.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize action_config", e);
        }
    }

    private static <T> T read(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to parse action_config as " + type.getSimpleName(), e);
        }
    }
}
