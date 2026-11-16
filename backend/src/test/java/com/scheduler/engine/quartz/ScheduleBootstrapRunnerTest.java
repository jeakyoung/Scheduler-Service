package com.scheduler.engine.quartz;

import com.scheduler.domain.schedule.ActionConfigJson;
import com.scheduler.domain.schedule.ActionType;
import com.scheduler.domain.schedule.DisplayOnlyActionConfig;
import com.scheduler.domain.schedule.ScheduleDefinition;
import com.scheduler.domain.schedule.ScheduleRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduleBootstrapRunnerTest {

    @Test
    void registersEveryEnabledScheduleOnStartup() {
        ScheduleRepository scheduleRepository = mock(ScheduleRepository.class);
        ScheduleRegistrar scheduleRegistrar = mock(ScheduleRegistrar.class);

        ScheduleDefinition first = schedule(1L);
        ScheduleDefinition second = schedule(2L);
        when(scheduleRepository.findAllEnabled()).thenReturn(List.of(first, second));

        new ScheduleBootstrapRunner(scheduleRepository, scheduleRegistrar).run(null);

        verify(scheduleRegistrar, times(1)).register(first);
        verify(scheduleRegistrar, times(1)).register(second);
    }

    @Test
    void registersNothingWhenNoEnabledSchedules() {
        ScheduleRepository scheduleRepository = mock(ScheduleRepository.class);
        ScheduleRegistrar scheduleRegistrar = mock(ScheduleRegistrar.class);
        when(scheduleRepository.findAllEnabled()).thenReturn(List.of());

        new ScheduleBootstrapRunner(scheduleRepository, scheduleRegistrar).run(null);

        verify(scheduleRegistrar, times(0)).register(org.mockito.ArgumentMatchers.any());
    }

    private ScheduleDefinition schedule(long id) {
        return new ScheduleDefinition(
                id, "Bootstrap test " + id, true, "0 0 9 ? * MON", "Asia/Seoul",
                "SP_SAMPLE", ActionType.DISPLAY_ONLY,
                ActionConfigJson.write(new DisplayOnlyActionConfig(null, null)),
                null, null, null, "tester"
        );
    }
}
