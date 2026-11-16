package com.scheduler.scenario;

import com.scheduler.domain.config.CredentialRepository;
import com.scheduler.domain.config.TargetDbConfig;
import com.scheduler.domain.config.TargetDbConfigRepository;
import com.scheduler.domain.schedule.ActionConfigJson;
import com.scheduler.domain.schedule.ActionType;
import com.scheduler.domain.schedule.ApiCallActionConfig;
import com.scheduler.domain.schedule.GroupingMode;
import com.scheduler.domain.schedule.ScheduleDefinition;
import com.scheduler.domain.schedule.ScheduleParameterRepository;
import com.scheduler.domain.schedule.ScheduleRepository;
import com.scheduler.engine.action.ApiCallActionHandler;
import com.scheduler.engine.action.DisplayOnlyActionHandler;
import com.scheduler.engine.execution.DisplayOutputStore;
import com.scheduler.engine.execution.ExecutionResult;
import com.scheduler.engine.execution.ExecutionStatus;
import com.scheduler.engine.execution.LastExecutionResultStore;
import com.scheduler.engine.execution.ScheduleExecutor;
import com.scheduler.engine.execution.TriggerType;
import com.scheduler.engine.sp.StoredProcedureInvoker;
import com.scheduler.infra.http.GenericHttpClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.CronExpression;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TASKS.md T14: reproduces the legacy system's 3 hardcoded execution patterns
 * (DaySchedulerTask / HourSchedulerTask / PurchaseReqSchedulerTask) as plain
 * schedule_definition data on the new generic ScheduleExecutor, each with no
 * schedule_parameter rows (matching the legacy "no parameters" calls) - proving the
 * legacy execution flow (SP call -> result processing -> downstream send) carries over,
 * not that every byte-for-byte legacy detail (company codes, fixed payload fields) does.
 */
class LegacyPatternScenarioTest {

    private static final long SCHEDULE_ID = 1L;
    private static final long TARGET_DB_CONFIG_ID = 10L;

    private ScheduleRepository scheduleRepository;
    private ScheduleParameterRepository scheduleParameterRepository;
    private TargetDbConfigRepository targetDbConfigRepository;
    private StoredProcedureInvoker storedProcedureInvoker;
    private CredentialRepository credentialRepository;

    private HttpServer httpServer;
    private final List<String> receivedBodies = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        scheduleRepository = mock(ScheduleRepository.class);
        scheduleParameterRepository = mock(ScheduleParameterRepository.class);
        targetDbConfigRepository = mock(TargetDbConfigRepository.class);
        storedProcedureInvoker = mock(StoredProcedureInvoker.class);
        credentialRepository = mock(CredentialRepository.class);

        // Legacy's 3 tasks all called their SP with no bound parameters - reproduced here by
        // schedule_parameter simply having no rows for this schedule.
        when(scheduleParameterRepository.findByScheduleId(SCHEDULE_ID)).thenReturn(List.of());
        when(targetDbConfigRepository.findById(TARGET_DB_CONFIG_ID)).thenReturn(Optional.of(
                new TargetDbConfig(TARGET_DB_CONFIG_ID, "legacy business db", "jdbc:sqlserver://unused;", "user", "pw", null, null)
        ));

        httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        httpServer.createContext("/push", exchange -> {
            receivedBodies.add(new String(exchange.getRequestBody().readAllBytes()));
            byte[] response = "{}".getBytes();
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        httpServer.start();
    }

    @AfterEach
    void tearDown() {
        httpServer.stop(0);
    }

    private String pushUrl() {
        return "http://localhost:" + httpServer.getAddress().getPort() + "/push";
    }

    private ScheduleExecutor newExecutor() {
        return new ScheduleExecutor(
                scheduleRepository,
                scheduleParameterRepository,
                targetDbConfigRepository,
                storedProcedureInvoker,
                Map.of(
                        ActionType.API_CALL, new ApiCallActionHandler(new GenericHttpClient(), credentialRepository),
                        ActionType.DISPLAY_ONLY, new DisplayOnlyActionHandler()
                ),
                result -> { },
                new DisplayOutputStore(),
                new LastExecutionResultStore()
        );
    }

    private Map<String, Object> row(Object... keyValuePairs) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            row.put((String) keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return row;
    }

    /**
     * Legacy DaySchedulerTask: SP_WORK_LIMIT_LIST, weekly Monday 9AM, 1 call per document
     * title grouped across all employees sharing it (legacy's own Java code built the
     * empList array; the generic engine's bodyTemplate substitution uses the group's first
     * row instead - PLAN.md §5's "그룹의 첫 행" rule, a deliberate generalization, not a
     * byte-for-byte replica).
     */
    @Test
    void weeklyMondayGroupedByTitle_noParameters() throws Exception {
        String cron = "0 0 9 ? * MON";
        assertThat(CronExpression.isValidExpression(cron)).isTrue();

        ApiCallActionConfig config = new ApiCallActionConfig(
                "POST", pushUrl(), Map.of(), GroupingMode.GROUP_BY, "EA_TITLE",
                "{\"notiGbn\":\"8\",\"empList\":[\"${EMPLOYEE_NO}\"],\"title\":\"업무연락 알림\",\"body\":\"${EA_TITLE}\"}"
        );
        ScheduleDefinition schedule = new ScheduleDefinition(
                SCHEDULE_ID, "Weekly work-limit notice (legacy Day pattern)", true, cron, "Asia/Seoul",
                "SP_WORK_LIMIT_LIST", TARGET_DB_CONFIG_ID, ActionType.API_CALL,
                ActionConfigJson.write(config), null, null, null, "tester"
        );
        when(scheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        when(storedProcedureInvoker.invoke(any(), any(), any(), any(), any())).thenReturn(List.of(
                row("EMPLOYEE_NO", "E001", "EA_TITLE", "Title A"),
                row("EMPLOYEE_NO", "E002", "EA_TITLE", "Title A"),
                row("EMPLOYEE_NO", "E003", "EA_TITLE", "Title B")
        ));

        ExecutionResult result = newExecutor().execute(SCHEDULE_ID, TriggerType.SCHEDULED);

        assertThat(result.status()).isEqualTo(ExecutionStatus.SUCCESS);
        // 3 rows, 2 distinct titles -> 2 HTTP calls (one per group), not 3 (not per-row)
        assertThat(receivedBodies).hasSize(2);
        assertThat(result.summary()).contains("2/2 성공");
    }

    /**
     * Legacy HourSchedulerTask: SP_WORK_LIMIT_LIST, weekdays every 30 minutes, 1 call per
     * employee (per SP result row) with payload. The legacy weekend-skip check
     * (DayOfWeek.SATURDAY/SUNDAY -> return) is fully absorbed into the MON-FRI cron field -
     * no runtime branch needed (PLAN.md §8 mapping table).
     */
    @Test
    void weekdayEveryThirtyMinutesPerRow_noParameters() throws Exception {
        String cron = "0 0/30 * ? * MON-FRI";
        assertThat(CronExpression.isValidExpression(cron)).isTrue();

        ApiCallActionConfig config = new ApiCallActionConfig(
                "POST", pushUrl(), Map.of(), GroupingMode.PER_ROW, null,
                "{\"notiGbn\":\"8\",\"empList\":[\"${EMPLOYEE_NO}\"],\"body\":\"${EA_TITLE}\",\"payload\":{\"eaExeId\":\"${EA_EXE_ID}\",\"exeSeq\":${EXE_SEQ}}}"
        );
        ScheduleDefinition schedule = new ScheduleDefinition(
                SCHEDULE_ID, "Work-limit-exceeded notice (legacy Hour pattern)", true, cron, "Asia/Seoul",
                "SP_WORK_LIMIT_LIST", TARGET_DB_CONFIG_ID, ActionType.API_CALL,
                ActionConfigJson.write(config), null, null, null, "tester"
        );
        when(scheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        when(storedProcedureInvoker.invoke(any(), any(), any(), any(), any())).thenReturn(List.of(
                row("EMPLOYEE_NO", "E001", "EA_TITLE", "Title A", "EA_EXE_ID", "EX1", "EXE_SEQ", 1),
                row("EMPLOYEE_NO", "E002", "EA_TITLE", "Title B", "EA_EXE_ID", "EX2", "EXE_SEQ", 2)
        ));

        ExecutionResult result = newExecutor().execute(SCHEDULE_ID, TriggerType.SCHEDULED);

        assertThat(result.status()).isEqualTo(ExecutionStatus.SUCCESS);
        // 2 rows, PER_ROW -> 2 individual HTTP calls (one per employee, not grouped)
        assertThat(receivedBodies).hasSize(2);
        assertThat(receivedBodies).anyMatch(b -> b.contains("E001"));
        assertThat(receivedBodies).anyMatch(b -> b.contains("E002"));
    }

    /**
     * Legacy PurchaseReqSchedulerTask: SP_PURCHASE_REQ_NOTICE, weekdays 8:30AM, 1 call per
     * unconfirmed-purchase-request recipient (per row) with payload - same PER_ROW/weekday
     * shape as the Hour pattern, different SP and cron time.
     */
    @Test
    void weekdayDailyAt0830PerRow_noParameters() throws Exception {
        String cron = "0 30 8 ? * MON-FRI";
        assertThat(CronExpression.isValidExpression(cron)).isTrue();

        ApiCallActionConfig config = new ApiCallActionConfig(
                "POST", pushUrl(), Map.of(), GroupingMode.PER_ROW, null,
                "{\"notiGbn\":\"8\",\"empList\":[\"${EMPLOYEE_NO}\"],\"title\":\"${EA_TITLE}\",\"payload\":{\"eaExeId\":\"${EA_EXE_ID}\"}}"
        );
        ScheduleDefinition schedule = new ScheduleDefinition(
                SCHEDULE_ID, "Unconfirmed purchase request notice (legacy PurchaseReq pattern)", true, cron, "Asia/Seoul",
                "SP_PURCHASE_REQ_NOTICE", TARGET_DB_CONFIG_ID, ActionType.API_CALL,
                ActionConfigJson.write(config), null, null, null, "tester"
        );
        when(scheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        when(storedProcedureInvoker.invoke(any(), any(), any(), any(), any())).thenReturn(List.of(
                row("EMPLOYEE_NO", "E001", "EA_TITLE", "PR Title A", "EA_EXE_ID", "PX1")
        ));

        ExecutionResult result = newExecutor().execute(SCHEDULE_ID, TriggerType.SCHEDULED);

        assertThat(result.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(receivedBodies).hasSize(1);
        assertThat(receivedBodies.get(0)).contains("E001").contains("PR Title A");
    }
}
