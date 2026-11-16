package com.scheduler.engine.execution;

/**
 * Per-target-DB execution detail (PLAN.md §3.1b) - one schedule run now visits N target DBs
 * sequentially (ScheduleExecutor), and each visit produces one of these. status is always
 * SUCCESS or FAILURE here (never PARTIAL_SUCCESS - that's only meaningful at the aggregate
 * ExecutionResult level once multiple of these are combined).
 */
public record TargetDbExecutionResult(
        long targetDbConfigId,
        String targetDbConfigName,
        ExecutionStatus status,
        String summary,
        String errorMessage
) {
}
