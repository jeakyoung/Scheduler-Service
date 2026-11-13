package com.scheduler.domain.schedule;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ActionConfigJsonTest {

    @Test
    void apiCallConfigRoundTripsExactly() {
        ApiCallActionConfig original = new ApiCallActionConfig(
                "POST",
                "https://notify.example.com/api/push",
                Map.of("SECURITY_CODE", "${cred:SAMPLE_SECURITY_CODE}", "X-Api-Key", "${cred:SAMPLE_API_KEY}"),
                GroupingMode.GROUP_BY,
                "EA_TITLE",
                "{\"title\":\"${EA_TITLE}\"}"
        );

        String json = ActionConfigJson.write(original);
        ApiCallActionConfig parsed = ActionConfigJson.readApiCall(json);

        assertThat(parsed).isEqualTo(original);
    }

    @Test
    void displayOnlyConfigRoundTripsExactly() {
        DisplayOnlyActionConfig original = new DisplayOnlyActionConfig("TABLE", List.of("COL_A", "COL_B"));

        String json = ActionConfigJson.write(original);
        DisplayOnlyActionConfig parsed = ActionConfigJson.readDisplayOnly(json);

        assertThat(parsed).isEqualTo(original);
    }

    @Test
    void displayOnlyConfigWithNullColumnsMeansAllColumns() {
        DisplayOnlyActionConfig original = new DisplayOnlyActionConfig("TABLE", null);

        String json = ActionConfigJson.write(original);

        assertThat(json).doesNotContain("columns");
        assertThat(ActionConfigJson.readDisplayOnly(json).columns()).isNull();
    }
}
