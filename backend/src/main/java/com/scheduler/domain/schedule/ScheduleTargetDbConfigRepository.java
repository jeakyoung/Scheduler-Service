package com.scheduler.domain.schedule;

import java.util.List;

/**
 * schedule_target_db_config join table (PLAN.md §4.3b) - a schedule can target multiple
 * target_db_config rows at once. No addOne/removeOne methods on purpose: callers always
 * replace a schedule's full target set in one go (PLAN.md §6 - the API takes the whole
 * targetDbConfigIds array per request), so a single transactional "replace" operation is the
 * only write path this needs.
 */
public interface ScheduleTargetDbConfigRepository {

    List<Long> findTargetDbConfigIdsByScheduleId(long scheduleId);

    /** Reverse lookup - used by TargetDbConfigController to block deleting a config still in use. */
    List<Long> findScheduleIdsByTargetDbConfigId(long targetDbConfigId);

    void replaceForSchedule(long scheduleId, List<Long> targetDbConfigIds);
}
