package com.scheduler.engine.execution;

public class ScheduleNotFoundException extends RuntimeException {
    public ScheduleNotFoundException(long scheduleId) {
        super("Schedule " + scheduleId + " not found");
    }
}
