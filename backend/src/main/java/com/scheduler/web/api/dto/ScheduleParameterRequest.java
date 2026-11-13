package com.scheduler.web.api.dto;

import com.scheduler.domain.schedule.ParamDataType;
import com.scheduler.domain.schedule.ValueSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ScheduleParameterRequest(
        @NotNull Integer paramOrder,
        @NotBlank String paramName,
        @NotNull ParamDataType dataType,
        @NotNull ValueSource valueSource,
        String literalValue
) {
}
