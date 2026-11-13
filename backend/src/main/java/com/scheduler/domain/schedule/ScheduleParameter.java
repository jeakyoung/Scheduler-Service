package com.scheduler.domain.schedule;

public record ScheduleParameter(
        Long id,
        Long scheduleId,
        int paramOrder,
        String paramName,
        ParamDataType dataType,
        ValueSource valueSource,
        String literalValue
) {
}
