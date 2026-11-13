package com.scheduler.engine.execution;

/**
 * Thrown by ScheduleExecutor when a run is already in progress for the same scheduleId -
 * the single guard that covers both "another scheduled run is in progress" and "manual
 * run-now while a scheduled run is in progress" (PLAN.md §8, generalizing the legacy
 * per-task static isTaskRunning flag).
 */
public class ScheduleAlreadyRunningException extends RuntimeException {
    public ScheduleAlreadyRunningException(long scheduleId) {
        super("Schedule " + scheduleId + " is already running");
    }
}
