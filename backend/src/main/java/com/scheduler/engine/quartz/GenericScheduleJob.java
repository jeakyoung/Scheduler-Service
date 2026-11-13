package com.scheduler.engine.quartz;

import com.scheduler.engine.execution.ScheduleAlreadyRunningException;
import com.scheduler.engine.execution.ScheduleExecutor;
import com.scheduler.engine.execution.TriggerType;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * JobDataMap carries only scheduleId (PLAN.md §2) - the full ScheduleDefinition is reloaded
 * from the meta DB on every fire via ScheduleExecutor, so an edited cron/action_config takes
 * effect on the very next run without re-registering anything here.
 *
 * Quartz instantiates this class itself via reflection (not through the Spring container),
 * so the @Autowired field below only gets populated because QuartzConfig installs a custom
 * JobFactory that autowires freshly-created Job instances.
 *
 * @DisallowConcurrentExecution stops Quartz from firing a second SCHEDULED run of the *same*
 * job while one is in progress; ScheduleExecutor's own guard additionally covers "a manual
 * run-now arrives while a SCHEDULED run is in progress" (PLAN.md §8).
 */
@DisallowConcurrentExecution
public class GenericScheduleJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(GenericScheduleJob.class);

    @Autowired
    private ScheduleExecutor scheduleExecutor;

    @Override
    public void execute(JobExecutionContext context) {
        long scheduleId = context.getJobDetail().getJobDataMap().getLong(ScheduleRegistrar.SCHEDULE_ID_KEY);
        try {
            scheduleExecutor.execute(scheduleId, TriggerType.SCHEDULED);
        } catch (ScheduleAlreadyRunningException e) {
            log.info("Skipped scheduled fire for schedule {} - already running", scheduleId);
        }
    }
}
