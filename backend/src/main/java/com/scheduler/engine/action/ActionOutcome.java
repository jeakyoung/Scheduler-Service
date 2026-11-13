package com.scheduler.engine.action;

import com.scheduler.engine.execution.DisplayOutput;

/**
 * summary/errorMessage must never contain decrypted credential values or full SP result
 * rows (PLAN.md §3.1/§5/§9) - they flow straight into ExecutionResult.
 */
public record ActionOutcome(
        boolean success,
        String summary,
        String errorMessage,
        DisplayOutput displayOutput
) {
}
