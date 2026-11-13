package com.scheduler.engine.execution;

import java.util.ArrayList;
import java.util.List;

/**
 * Proves ScheduleExecutor depends only on the ExecutionResultSink port: swapping
 * LoggingExecutionResultSink for this in tests requires zero ScheduleExecutor changes
 * (PLAN.md §3.2's completion criterion for this round).
 */
public class FakeExecutionResultSink implements ExecutionResultSink {

    private final List<ExecutionResult> recorded = new ArrayList<>();

    @Override
    public void record(ExecutionResult result) {
        recorded.add(result);
    }

    public List<ExecutionResult> recorded() {
        return recorded;
    }
}
