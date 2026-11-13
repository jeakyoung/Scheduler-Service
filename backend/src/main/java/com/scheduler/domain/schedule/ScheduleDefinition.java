package com.scheduler.domain.schedule;

import java.time.Instant;

/**
 * actionConfig is kept as a raw JSON string here - its shape depends on actionType
 * (ApiCallActionConfig vs DisplayOnlyActionConfig, see ActionConfigJson) and is only
 * parsed by the engine/action handler that knows which shape applies (PLAN.md §5).
 */
public record ScheduleDefinition(
        Long id,
        String name,
        boolean enabled,
        String cronExpression,
        String timezone,
        String spName,
        Long targetDbConfigId,
        ActionType actionType,
        String actionConfig,
        String description,
        Instant createdAt,
        Instant updatedAt,
        String updatedBy
) {
}
