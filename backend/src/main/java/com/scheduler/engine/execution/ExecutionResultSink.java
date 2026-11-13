package com.scheduler.engine.execution;

/**
 * Port that decouples ScheduleExecutor from any particular persistence/observability
 * backend for execution results (PLAN.md §3.2). LoggingExecutionResultSink is the only
 * implementation for now; a future JdbcExecutionResultSink (T15, once the history table is
 * designed) can be added or composed without ScheduleExecutor changing at all.
 */
public interface ExecutionResultSink {
    void record(ExecutionResult result);
}
