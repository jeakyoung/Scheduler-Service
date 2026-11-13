package com.scheduler.web.api.dto;

import java.time.Instant;

public record ScheduleStatusResponse(
        long scheduleId,
        Instant nextFireTime,
        boolean running
) {
}
