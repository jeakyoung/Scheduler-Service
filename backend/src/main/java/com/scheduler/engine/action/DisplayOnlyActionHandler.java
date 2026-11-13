package com.scheduler.engine.action;

import com.scheduler.domain.schedule.ActionConfigJson;
import com.scheduler.domain.schedule.DisplayOnlyActionConfig;
import com.scheduler.domain.schedule.ScheduleDefinition;
import com.scheduler.engine.execution.DisplayOutput;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SP result -> DisplayOutput, no external call (PLAN.md §5 action_type=DISPLAY_ONLY). The
 * one downstream mode the legacy system never had (it only ever pushed to FCM).
 */
@Component
public class DisplayOnlyActionHandler implements ActionHandler {

    @Override
    public ActionOutcome handle(ScheduleDefinition schedule, List<Map<String, Object>> spResultRows) {
        DisplayOnlyActionConfig config = ActionConfigJson.readDisplayOnly(schedule.actionConfig());

        List<String> columns = config.columns() != null
                ? config.columns()
                : (spResultRows.isEmpty() ? List.of() : List.copyOf(spResultRows.get(0).keySet()));

        List<Map<String, Object>> projectedRows = new ArrayList<>(spResultRows.size());
        for (Map<String, Object> row : spResultRows) {
            Map<String, Object> projected = new LinkedHashMap<>();
            for (String column : columns) {
                projected.put(column, row.get(column));
            }
            projectedRows.add(projected);
        }

        DisplayOutput output = DisplayOutput.of(schedule.id(), columns, projectedRows);
        String summary = "SP 결과 %d행 가공 완료".formatted(spResultRows.size());
        return new ActionOutcome(true, summary, null, output);
    }
}
