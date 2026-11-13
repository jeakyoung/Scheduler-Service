package com.scheduler.engine.action;

import com.scheduler.domain.schedule.ScheduleDefinition;

import java.util.List;
import java.util.Map;

public interface ActionHandler {
    ActionOutcome handle(ScheduleDefinition schedule, List<Map<String, Object>> spResultRows);
}
