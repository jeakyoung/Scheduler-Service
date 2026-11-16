package com.scheduler.engine.execution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class LoggingExecutionResultSink implements ExecutionResultSink {

    private static final Logger log = LoggerFactory.getLogger(LoggingExecutionResultSink.class);

    @Override
    public void record(ExecutionResult result) {
        switch (result.status()) {
            case SUCCESS -> log.info("Schedule {} ({}) succeeded: {}",
                    result.scheduleId(), result.triggerType(), result.summary());
            case PARTIAL_SUCCESS -> log.warn("Schedule {} ({}) partially succeeded: {} - {}",
                    result.scheduleId(), result.triggerType(), result.summary(), failureDetail(result));
            case FAILURE -> log.error("Schedule {} ({}) failed: {} - {}",
                    result.scheduleId(), result.triggerType(), result.summary(), failureDetail(result));
        }
    }

    /**
     * Top-level errorMessage is schedule/engine-level only (PLAN.md §3.1) and is null once
     * execution reaches the per-target-DB loop - the per-target detail lives in
     * targetDbResults instead, so this falls back to summarizing those for the log line.
     */
    private static String failureDetail(ExecutionResult result) {
        if (result.errorMessage() != null) {
            return result.errorMessage();
        }
        return result.targetDbResults().stream()
                .filter(targetDbResult -> targetDbResult.status() == ExecutionStatus.FAILURE)
                .map(targetDbResult -> targetDbResult.targetDbConfigName() + ": " + targetDbResult.errorMessage())
                .collect(Collectors.joining("; "));
    }
}
