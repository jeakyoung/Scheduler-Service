package com.scheduler.web.api.dto;

import com.scheduler.domain.schedule.ParamDataType;
import com.scheduler.domain.schedule.ValueSource;

public record ScheduleParameterResponse(
        long id,
        int paramOrder,
        String paramName,
        ParamDataType dataType,
        ValueSource valueSource,
        String literalValue
) {
}
