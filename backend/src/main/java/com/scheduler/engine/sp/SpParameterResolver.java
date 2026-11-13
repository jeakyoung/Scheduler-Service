package com.scheduler.engine.sp;

import com.scheduler.domain.schedule.ParamDataType;
import com.scheduler.domain.schedule.ScheduleParameter;
import com.scheduler.domain.schedule.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Resolves schedule_parameter rows into actual bound values at execution time (PLAN.md §4.2).
 * New value_source kinds are added as ValueSource enum values + a case here only - no schema
 * change needed.
 */
public final class SpParameterResolver {

    private SpParameterResolver() {
    }

    public static List<Object> resolve(List<ScheduleParameter> parameters) {
        return parameters.stream()
                .sorted(Comparator.comparingInt(ScheduleParameter::paramOrder))
                .map(SpParameterResolver::resolveOne)
                .toList();
    }

    private static Object resolveOne(ScheduleParameter parameter) {
        return switch (parameter.valueSource()) {
            case CURRENT_DATE -> LocalDate.now();
            case CURRENT_DATETIME -> LocalDateTime.now();
            case LITERAL -> parseLiteral(parameter.dataType(), parameter.literalValue());
        };
    }

    private static Object parseLiteral(ParamDataType dataType, String literalValue) {
        return switch (dataType) {
            case STRING -> literalValue;
            case INT -> Integer.parseInt(literalValue);
            case DECIMAL -> new BigDecimal(literalValue);
            case DATE -> LocalDate.parse(literalValue);
            case DATETIME -> LocalDateTime.parse(literalValue);
            case BOOLEAN -> Boolean.parseBoolean(literalValue);
        };
    }
}
