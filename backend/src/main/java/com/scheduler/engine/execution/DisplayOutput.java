package com.scheduler.engine.execution;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * DISPLAY_ONLY mode's result (PLAN.md §3.3) - "what to show right now", not execution
 * history. MAX_ROWS caps memory use for SPs that return unexpectedly large result sets;
 * this is a snapshot cache, not an archive (PLAN.md §9's "monitoring, not backup" principle).
 *
 * 2026-11-16 (PLAN.md §3.3, multi-target-DB round): one of these now holds exactly one target
 * DB's rows - never merged across target DBs (PLAN.md §0/§5's "don't blur which company's data
 * this is" principle). ActionHandler implementations (e.g. DisplayOnlyActionHandler) don't
 * know which target DB they're processing (PLAN.md §3.1b - that knowledge belongs to
 * ScheduleExecutor alone), so they still build one via of(...) with placeholder target fields,
 * and ScheduleExecutor stamps the real targetDbConfigId/Name on afterwards via withTarget(...).
 */
public record DisplayOutput(
        long scheduleId,
        long targetDbConfigId,
        String targetDbConfigName,
        List<String> columns,
        List<Map<String, Object>> rows,
        Instant generatedAt
) {
    public static final int MAX_ROWS = 500;

    public static DisplayOutput of(long scheduleId, List<String> columns, List<Map<String, Object>> rows) {
        List<Map<String, Object>> capped = rows.size() > MAX_ROWS ? rows.subList(0, MAX_ROWS) : rows;
        return new DisplayOutput(scheduleId, 0L, null, columns, List.copyOf(capped), Instant.now());
    }

    public DisplayOutput withTarget(long targetDbConfigId, String targetDbConfigName) {
        return new DisplayOutput(scheduleId, targetDbConfigId, targetDbConfigName, columns, rows, generatedAt);
    }
}
