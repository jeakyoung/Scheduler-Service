package com.scheduler.engine.quartz;

import com.scheduler.domain.schedule.ScheduleRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Replaces the legacy SchedulerContextListener.contextInitialized()'s 3 hardcoded
 * scheduleAtFixedRate calls: on every startup, every enabled schedule_definition row gets
 * registered with Quartz (PLAN.md §8).
 */
@Component
public class ScheduleBootstrapRunner implements ApplicationRunner {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleRegistrar scheduleRegistrar;

    public ScheduleBootstrapRunner(ScheduleRepository scheduleRepository, ScheduleRegistrar scheduleRegistrar) {
        this.scheduleRepository = scheduleRepository;
        this.scheduleRegistrar = scheduleRegistrar;
    }

    @Override
    public void run(ApplicationArguments args) {
        scheduleRepository.findAllEnabled().forEach(scheduleRegistrar::register);
    }
}
