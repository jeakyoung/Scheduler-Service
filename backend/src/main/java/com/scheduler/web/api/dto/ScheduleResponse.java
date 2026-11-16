package com.scheduler.web.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.scheduler.domain.schedule.ActionType;

import java.time.Instant;
import java.util.List;

/**
 * parameters is populated for the single-schedule GET ("단건 조회(파라미터 포함)", PLAN.md §6)
 * and left empty for the list endpoint to avoid an N+1 query there.
 */
public record ScheduleResponse(
        long id,
        String name,
        boolean enabled,
        String cronExpression,
        String timezone,
        String spName,
        List<Long> targetDbConfigIds,
        List<TargetDbConfigRef> targetDbConfigs,
        ActionType actionType,
        JsonNode actionConfig,
        String description,
        Instant createdAt,
        Instant updatedAt,
        String updatedBy,
        Instant nextFireTime,
        List<ScheduleParameterResponse> parameters
) {
}
