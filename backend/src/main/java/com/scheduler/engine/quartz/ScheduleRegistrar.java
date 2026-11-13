package com.scheduler.engine.quartz;

import com.scheduler.domain.schedule.ScheduleDefinition;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

/**
 * Thin wrapper around Quartz's scheduleJob/deleteJob, called by admin CRUD (T8) and the
 * startup bootstrap runner below (PLAN.md §2/§8 - replaces the legacy hardcoded
 * scheduleAtFixedRate calls with dynamic, DB-driven registration).
 */
@Component
public class ScheduleRegistrar {

    static final String SCHEDULE_ID_KEY = "scheduleId";

    private final Scheduler scheduler;

    public ScheduleRegistrar(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Registers (or re-registers, if already present) the given schedule's Quartz job+trigger.
     * A disabled schedule is simply unregistered - callers don't need to branch on enabled().
     */
    public void register(ScheduleDefinition schedule) {
        try {
            unregister(schedule.id());
            if (!schedule.enabled()) {
                return;
            }

            JobDetail jobDetail = JobBuilder.newJob(GenericScheduleJob.class)
                    .withIdentity(jobKey(schedule.id()))
                    .usingJobData(SCHEDULE_ID_KEY, schedule.id())
                    .storeDurably()
                    .build();

            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey(schedule.id()))
                    .forJob(jobDetail)
                    .withSchedule(CronScheduleBuilder.cronSchedule(schedule.cronExpression())
                            .inTimeZone(java.util.TimeZone.getTimeZone(ZoneId.of(schedule.timezone()))))
                    .build();

            scheduler.scheduleJob(jobDetail, trigger);
        } catch (SchedulerException e) {
            throw new QuartzRegistrationException("Failed to register schedule " + schedule.id(), e);
        }
    }

    public void reschedule(ScheduleDefinition schedule) {
        register(schedule);
    }

    public void unregister(long scheduleId) {
        try {
            scheduler.deleteJob(jobKey(scheduleId));
        } catch (SchedulerException e) {
            throw new QuartzRegistrationException("Failed to unregister schedule " + scheduleId, e);
        }
    }

    private static JobKey jobKey(long scheduleId) {
        return JobKey.jobKey("schedule-" + scheduleId);
    }

    private static TriggerKey triggerKey(long scheduleId) {
        return TriggerKey.triggerKey("schedule-" + scheduleId + "-trigger");
    }
}
