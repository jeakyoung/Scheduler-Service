package com.scheduler.engine.execution;

import java.time.Instant;

/**
 * Not persisted anywhere yet (PLAN.md §3.1/§9 - execution history table is TBD, future
 * round). summary/errorMessage are deliberately summary-level, never the full SP result set
 * (PLAN.md's "monitoring server, not a backup server" principle).
 */
public record ExecutionResult(
        long scheduleId,
        TriggerType triggerType,
        Instant startedAt,
        Instant finishedAt,
        ExecutionStatus status,
        String summary,
        String errorMessage
) {
}
