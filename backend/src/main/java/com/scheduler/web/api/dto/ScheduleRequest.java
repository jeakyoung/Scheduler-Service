package com.scheduler.web.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.scheduler.domain.schedule.ActionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ScheduleRequest(
        @NotBlank String name,
        boolean enabled,
        @NotBlank String cronExpression,
        @NotBlank String timezone,
        @NotBlank String spName,
        // At least one target DB is required (PLAN.md §4.3b/§6) - existence of each id is
        // checked separately in ScheduleController (a plain @NotNull Long can't express an FK).
        @NotEmpty List<Long> targetDbConfigIds,
        @NotNull ActionType actionType,
        @NotNull JsonNode actionConfig,
        String description
) {
}
