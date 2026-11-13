package com.scheduler.domain.schedule;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * action_config shape when action_type = DISPLAY_ONLY (PLAN.md §5). columns == null means
 * "show every column the SP returned".
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DisplayOnlyActionConfig(
        String displayFormat,
        List<String> columns
) {
}
