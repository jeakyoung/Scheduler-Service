package com.scheduler.web.api.dto;

import com.scheduler.engine.execution.DisplayOutput;
import com.scheduler.engine.execution.ExecutionResult;

/**
 * Shared response shape for run-now and last-result (PLAN.md §6) - displayOutput is only
 * non-null for DISPLAY_ONLY schedules.
 */
public record RunResultResponse(
        ExecutionResult result,
        DisplayOutput displayOutput
) {
}
