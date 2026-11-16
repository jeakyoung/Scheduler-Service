package com.scheduler.engine.execution;

import java.time.Instant;
import java.util.List;

/**
 * Not persisted anywhere yet (PLAN.md §3.1/§9 - execution history table is TBD, future
 * round). summary/errorMessage are deliberately summary-level, never the full SP result set
 * (PLAN.md's "monitoring server, not a backup server" principle).
 *
 * 2026-11-16 (PLAN.md §3.1, multi-target-DB round): a schedule run now visits N target DBs
 * (ScheduleExecutor), so this top-level record is the overall summary across all of them -
 * errorMessage stays schedule/engine-level only (e.g. "schedule definition not found"); a
 * single target DB's own failure detail lives in targetDbResults[i].errorMessage, never here.
 */
public record ExecutionResult(
        long scheduleId,
        TriggerType triggerType,
        Instant startedAt,
        Instant finishedAt,
        ExecutionStatus status,
        String summary,
        String errorMessage,
        List<TargetDbExecutionResult> targetDbResults
) {
}
