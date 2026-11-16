package com.scheduler.engine.execution;

/**
 * PARTIAL_SUCCESS only applies at the top-level ExecutionResult (PLAN.md §3.1 - some target
 * DBs succeeded, some failed). TargetDbExecutionResult reuses this same enum but, by
 * definition, only ever takes SUCCESS/FAILURE (PLAN.md §3.1b: a single target DB has no
 * "partial" outcome of its own).
 */
public enum ExecutionStatus {
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILURE
}
