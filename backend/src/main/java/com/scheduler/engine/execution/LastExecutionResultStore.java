package com.scheduler.engine.execution;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Latest-ExecutionResult-only in-memory cache, keyed by scheduleId - backs GET
 * .../last-result (PLAN.md §6) for schedules of either action_type. Companion to
 * DisplayOutputStore, which only ever holds something for DISPLAY_ONLY schedules; this one
 * is populated on every execute() call regardless of outcome or action_type.
 */
@Component
public class LastExecutionResultStore {

    private final Map<Long, ExecutionResult> latestByScheduleId = new ConcurrentHashMap<>();

    public void put(ExecutionResult result) {
        latestByScheduleId.put(result.scheduleId(), result);
    }

    public Optional<ExecutionResult> get(long scheduleId) {
        return Optional.ofNullable(latestByScheduleId.get(scheduleId));
    }
}
