package com.scheduler.domain.schedule;

import java.util.List;

public interface ScheduleParameterRepository {

    List<ScheduleParameter> findByScheduleId(long scheduleId);

    /** Deletes all existing parameters for the schedule and inserts the given list (PLAN.md §6: "통째 교체"). */
    List<ScheduleParameter> replaceAll(long scheduleId, List<ScheduleParameter> parameters);

    void deleteByScheduleId(long scheduleId);
}
