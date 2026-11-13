package com.scheduler.web.api;

import com.scheduler.domain.schedule.ActionConfigJson;
import com.scheduler.domain.schedule.ActionType;
import com.scheduler.domain.schedule.ApiCallActionConfig;
import com.scheduler.domain.schedule.DisplayOnlyActionConfig;
import com.scheduler.domain.schedule.GroupingMode;

/**
 * Enforces that action_config actually has the shape its action_type requires
 * (PLAN.md §5/TASKS.md T8 - e.g. API_CALL without a url is a 400, not a runtime surprise
 * during the next scheduled fire).
 */
final class ActionConfigRequestValidator {

    private ActionConfigRequestValidator() {
    }

    static void validate(ActionType actionType, String actionConfigJson) {
        switch (actionType) {
            case API_CALL -> validateApiCall(ActionConfigJson.readApiCall(actionConfigJson));
            case DISPLAY_ONLY -> ActionConfigJson.readDisplayOnly(actionConfigJson);
        }
    }

    private static void validateApiCall(ApiCallActionConfig config) {
        require(config.method() != null && !config.method().isBlank(), "action_config.method is required for API_CALL");
        require(config.url() != null && !config.url().isBlank(), "action_config.url is required for API_CALL");
        require(config.groupingMode() != null, "action_config.groupingMode is required for API_CALL");
        if (config.groupingMode() == GroupingMode.GROUP_BY) {
            require(config.groupByField() != null && !config.groupByField().isBlank(),
                    "action_config.groupByField is required when groupingMode is GROUP_BY");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
