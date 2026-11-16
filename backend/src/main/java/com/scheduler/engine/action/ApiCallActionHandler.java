package com.scheduler.engine.action;

import com.scheduler.domain.config.CredentialRepository;
import com.scheduler.domain.schedule.ActionConfigJson;
import com.scheduler.domain.schedule.ApiCallActionConfig;
import com.scheduler.domain.schedule.GroupingMode;
import com.scheduler.domain.schedule.ScheduleDefinition;
import com.scheduler.infra.http.GenericHttpClient;
import com.scheduler.infra.http.HttpCallResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generalizes the legacy executeTask2() pattern: arbitrary URL/method/headers/bodyTemplate,
 * one HTTP call per row (PER_ROW, the Hour/PurchaseReq legacy pattern) or per group
 * (GROUP_BY, the Day legacy pattern - grouped by groupByField) (PLAN.md §5, §8). FCM is just
 * one possible caller of this, never hardcoded here.
 */
@Component
public class ApiCallActionHandler implements ActionHandler {

    private final GenericHttpClient httpClient;
    private final CredentialRepository credentialRepository;

    public ApiCallActionHandler(GenericHttpClient httpClient, CredentialRepository credentialRepository) {
        this.httpClient = httpClient;
        this.credentialRepository = credentialRepository;
    }

    @Override
    public ActionOutcome handle(ScheduleDefinition schedule, List<Map<String, Object>> spResultRows) {
        ApiCallActionConfig config = ActionConfigJson.readApiCall(schedule.actionConfig());
        List<List<Map<String, Object>>> callGroups = groupRows(spResultRows, config);

        int successCount = 0;
        String lastErrorMessage = null;

        for (List<Map<String, Object>> group : callGroups) {
            Map<String, Object> templateRow = group.get(0);
            try {
                // bodyTemplate is JSON (PLAN.md §5) - renderJson escapes substituted values
                // so a column/credential value can't break the JSON structure or inject
                // fields (REVIEW.md N1). Header values aren't JSON, so they stay unescaped.
                String body = BodyTemplateRenderer.renderJson(config.bodyTemplate(), templateRow, this::resolveCredential);
                Map<String, String> headers = renderHeaders(config.headers(), templateRow);

                HttpCallResult response = httpClient.call(config.method(), config.url(), headers, body);
                if (response.isSuccess()) {
                    successCount++;
                } else {
                    lastErrorMessage = "HTTP " + response.statusCode();
                }
            } catch (RuntimeException e) {
                lastErrorMessage = e.getMessage();
            }
        }

        int total = callGroups.size();
        boolean allSucceeded = successCount == total;
        String summary = "처리행 %d건, API 호출 %d/%d 성공".formatted(spResultRows.size(), successCount, total);

        return new ActionOutcome(allSucceeded, summary, allSucceeded ? null : lastErrorMessage, null);
    }

    private List<List<Map<String, Object>>> groupRows(List<Map<String, Object>> rows, ApiCallActionConfig config) {
        if (config.groupingMode() != GroupingMode.GROUP_BY) {
            return rows.stream().map(List::of).toList();
        }

        Map<Object, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            grouped.computeIfAbsent(row.get(config.groupByField()), k -> new java.util.ArrayList<>()).add(row);
        }
        return List.copyOf(grouped.values());
    }

    private Map<String, String> renderHeaders(Map<String, String> headerTemplates, Map<String, Object> templateRow) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (headerTemplates != null) {
            headerTemplates.forEach((key, valueTemplate) ->
                    headers.put(key, BodyTemplateRenderer.render(valueTemplate, templateRow, this::resolveCredential)));
        }
        return headers;
    }

    private String resolveCredential(String name) {
        return credentialRepository.findByName(name)
                .map(com.scheduler.domain.config.Credential::value)
                .orElseThrow(() -> new IllegalStateException("Unknown credential referenced in action_config: " + name));
    }
}
