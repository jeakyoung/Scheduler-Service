package com.scheduler.web.api.dto;

import com.scheduler.engine.execution.DisplayOutput;
import com.scheduler.engine.execution.ExecutionResult;

import java.util.List;

/**
 * Shared response shape for run-now and last-result (PLAN.md §6). displayOutputs is only
 * non-empty for DISPLAY_ONLY schedules, and holds at most one entry per target DB that
 * succeeded in this result's targetDbResults (2026-11-16, multi-target-DB round - previously
 * a single nullable DisplayOutput, now one per target DB, never merged - PLAN.md §3.3).
 */
public record RunResultResponse(
        ExecutionResult result,
        List<DisplayOutput> displayOutputs
) {
}
