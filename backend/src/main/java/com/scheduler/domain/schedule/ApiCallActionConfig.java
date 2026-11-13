package com.scheduler.domain.schedule;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * action_config shape when action_type = API_CALL (PLAN.md §5). Arbitrary URL/method/
 * headers/body template - FCM is just one possible caller, never hardcoded here.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiCallActionConfig(
        String method,
        String url,
        Map<String, String> headers,
        GroupingMode groupingMode,
        String groupByField,
        String bodyTemplate
) {
}
