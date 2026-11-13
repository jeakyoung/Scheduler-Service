package com.scheduler.engine.sp;

import com.scheduler.domain.schedule.ParamDataType;
import com.scheduler.domain.schedule.ScheduleParameter;
import com.scheduler.domain.schedule.ValueSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpParameterResolverTest {

    @Test
    void noParametersResolvesToEmptyList() {
        assertThat(SpParameterResolver.resolve(List.of())).isEmpty();
    }

    @Test
    void resolvesInParamOrderRegardlessOfInputOrder() {
        ScheduleParameter second = new ScheduleParameter(null, 1L, 2, "@Status", ParamDataType.STRING, ValueSource.LITERAL, "ACTIVE");
        ScheduleParameter first = new ScheduleParameter(null, 1L, 1, "@FromDate", ParamDataType.DATE, ValueSource.LITERAL, "2026-01-01");

        List<Object> resolved = SpParameterResolver.resolve(List.of(second, first));

        assertThat(resolved).containsExactly(LocalDate.parse("2026-01-01"), "ACTIVE");
    }

    @Test
    void currentDateAndDatetimeAreResolvedAtCallTime() {
        ScheduleParameter dateParam = new ScheduleParameter(null, 1L, 1, "@Today", ParamDataType.DATE, ValueSource.CURRENT_DATE, null);
        ScheduleParameter datetimeParam = new ScheduleParameter(null, 1L, 2, "@Now", ParamDataType.DATETIME, ValueSource.CURRENT_DATETIME, null);

        List<Object> resolved = SpParameterResolver.resolve(List.of(dateParam, datetimeParam));

        assertThat(resolved.get(0)).isInstanceOf(LocalDate.class).isEqualTo(LocalDate.now());
        assertThat(resolved.get(1)).isInstanceOf(LocalDateTime.class);
    }

    @Test
    void literalValuesAreParsedPerDataType() {
        List<ScheduleParameter> params = List.of(
                new ScheduleParameter(null, 1L, 1, "@S", ParamDataType.STRING, ValueSource.LITERAL, "hello"),
                new ScheduleParameter(null, 1L, 2, "@I", ParamDataType.INT, ValueSource.LITERAL, "42"),
                new ScheduleParameter(null, 1L, 3, "@D", ParamDataType.DECIMAL, ValueSource.LITERAL, "3.14"),
                new ScheduleParameter(null, 1L, 4, "@B", ParamDataType.BOOLEAN, ValueSource.LITERAL, "true")
        );

        List<Object> resolved = SpParameterResolver.resolve(params);

        assertThat(resolved).containsExactly("hello", 42, new BigDecimal("3.14"), true);
    }
}
