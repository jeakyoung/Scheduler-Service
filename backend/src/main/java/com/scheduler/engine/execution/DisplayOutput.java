package com.scheduler.engine.execution;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * DISPLAY_ONLY mode's result (PLAN.md §3.3) - "what to show right now", not execution
 * history. MAX_ROWS caps memory use for SPs that return unexpectedly large result sets;
 * this is a snapshot cache, not an archive (PLAN.md §9's "monitoring, not backup" principle).
 */
public record DisplayOutput(
        long scheduleId,
        List<String> columns,
        List<Map<String, Object>> rows,
        Instant generatedAt
) {
    public static final int MAX_ROWS = 500;

    public static DisplayOutput of(long scheduleId, List<String> columns, List<Map<String, Object>> rows) {
        List<Map<String, Object>> capped = rows.size() > MAX_ROWS ? rows.subList(0, MAX_ROWS) : rows;
        return new DisplayOutput(scheduleId, columns, List.copyOf(capped), Instant.now());
    }
}
