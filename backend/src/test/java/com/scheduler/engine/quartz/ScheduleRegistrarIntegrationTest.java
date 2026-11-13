package com.scheduler.engine.quartz;

import com.scheduler.domain.schedule.ActionConfigJson;
import com.scheduler.domain.schedule.ActionType;
import com.scheduler.domain.schedule.DisplayOnlyActionConfig;
import com.scheduler.domain.schedule.ScheduleDefinition;
import org.junit.jupiter.api.Test;
import org.quartz.CronExpression;
import org.quartz.CronTrigger;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleRegistrarIntegrationTest extends AbstractQuartzIntegrationTest {

    @Autowired
    private ScheduleRegistrar registrar;

    @Autowired
    private Scheduler scheduler;

    @Test
    void registerCreatesTriggerWithNextFireTimeMatchingCron() throws Exception {
        long scheduleId = 9001L;
        String cron = "0 0 9 ? * MON";
        Date referenceNow = new Date();

        registrar.register(schedule(scheduleId, cron, "Asia/Seoul", true));

        Trigger trigger = scheduler.getTrigger(triggerKey(scheduleId));
        assertThat(trigger).isNotNull();
        assertThat(((CronTrigger) trigger).getCronExpression()).isEqualTo(cron);

        CronExpression cronExpression = new CronExpression(cron);
        cronExpression.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
        Date expectedNextFire = cronExpression.getNextValidTimeAfter(referenceNow);

        assertThat(trigger.getNextFireTime()).isEqualTo(expectedNextFire);
    }

    @Test
    void unregisterRemovesJobAndTrigger() throws Exception {
        long scheduleId = 9002L;
        registrar.register(schedule(scheduleId, "0 0 9 ? * MON", "Asia/Seoul", true));
        assertThat(scheduler.checkExists(jobKey(scheduleId))).isTrue();

        registrar.unregister(scheduleId);

        assertThat(scheduler.checkExists(jobKey(scheduleId))).isFalse();
        assertThat(scheduler.getTrigger(triggerKey(scheduleId))).isNull();
    }

    @Test
    void reRegisteringWithDifferentCronChangesNextFireTime() throws Exception {
        long scheduleId = 9003L;
        registrar.register(schedule(scheduleId, "0 0 9 ? * MON", "Asia/Seoul", true));
        Date firstNextFire = scheduler.getTrigger(triggerKey(scheduleId)).getNextFireTime();

        String newCron = "0 30 8 ? * TUE";
        registrar.register(schedule(scheduleId, newCron, "Asia/Seoul", true));
        Trigger updatedTrigger = scheduler.getTrigger(triggerKey(scheduleId));

        assertThat(((CronTrigger) updatedTrigger).getCronExpression()).isEqualTo(newCron);
        assertThat(updatedTrigger.getNextFireTime()).isNotEqualTo(firstNextFire);
    }

    @Test
    void registeringDisabledScheduleDoesNotCreateATrigger() throws Exception {
        long scheduleId = 9004L;
        registrar.register(schedule(scheduleId, "0 0 9 ? * MON", "Asia/Seoul", false));

        assertThat(scheduler.checkExists(jobKey(scheduleId))).isFalse();
    }

    private ScheduleDefinition schedule(long id, String cron, String timezone, boolean enabled) {
        return new ScheduleDefinition(
                id, "Quartz test schedule " + id, enabled, cron, timezone,
                "SP_SAMPLE", 1L, ActionType.DISPLAY_ONLY,
                ActionConfigJson.write(new DisplayOnlyActionConfig(null, null)),
                null, null, null, "tester"
        );
    }

    private JobKey jobKey(long scheduleId) {
        return JobKey.jobKey("schedule-" + scheduleId);
    }

    private TriggerKey triggerKey(long scheduleId) {
        return TriggerKey.triggerKey("schedule-" + scheduleId + "-trigger");
    }
}
