package com.scheduler.engine.execution;

import com.scheduler.domain.config.Credential;
import com.scheduler.domain.config.CredentialRepository;
import com.scheduler.domain.config.TargetDbConfig;
import com.scheduler.domain.config.TargetDbConfigRepository;
import com.scheduler.domain.schedule.ActionConfigJson;
import com.scheduler.domain.schedule.ActionType;
import com.scheduler.domain.schedule.ApiCallActionConfig;
import com.scheduler.domain.schedule.DisplayOnlyActionConfig;
import com.scheduler.domain.schedule.GroupingMode;
import com.scheduler.domain.schedule.ScheduleDefinition;
import com.scheduler.domain.schedule.ScheduleParameterRepository;
import com.scheduler.domain.schedule.ScheduleRepository;
import com.scheduler.engine.action.ApiCallActionHandler;
import com.scheduler.engine.action.DisplayOnlyActionHandler;
import com.scheduler.engine.sp.StoredProcedureExecutionException;
import com.scheduler.engine.sp.StoredProcedureInvoker;
import com.scheduler.infra.http.GenericHttpClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScheduleExecutorTest {

    private static final long SCHEDULE_ID = 1L;
    private static final long TARGET_DB_CONFIG_ID = 10L;

    private ScheduleRepository scheduleRepository;
    private ScheduleParameterRepository scheduleParameterRepository;
    private TargetDbConfigRepository targetDbConfigRepository;
    private StoredProcedureInvoker storedProcedureInvoker;
    private CredentialRepository credentialRepository;
    private FakeExecutionResultSink sink;
    private DisplayOutputStore displayOutputStore;

    private HttpServer httpServer;
    private final List<String> receivedBodies = new CopyOnWriteArrayList<>();
    private final AtomicInteger callCount = new AtomicInteger();
    private volatile int[] responseStatusesInOrder = {200};

    @BeforeEach
    void setUp() throws IOException {
        scheduleRepository = mock(ScheduleRepository.class);
        scheduleParameterRepository = mock(ScheduleParameterRepository.class);
        targetDbConfigRepository = mock(TargetDbConfigRepository.class);
        storedProcedureInvoker = mock(StoredProcedureInvoker.class);
        credentialRepository = mock(CredentialRepository.class);
        sink = new FakeExecutionResultSink();
        displayOutputStore = new DisplayOutputStore();

        when(scheduleParameterRepository.findByScheduleId(anyLong())).thenReturn(List.of());
        when(targetDbConfigRepository.findById(TARGET_DB_CONFIG_ID)).thenReturn(Optional.of(
                new TargetDbConfig(TARGET_DB_CONFIG_ID, "test db", "jdbc:sqlserver://unused;", "user", "pw", null, null)
        ));

        httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        httpServer.createContext("/push", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes());
            receivedBodies.add(body);
            int index = Math.min(callCount.getAndIncrement(), responseStatusesInOrder.length - 1);
            int status = responseStatusesInOrder[index];
            byte[] response = "{}".getBytes();
            exchange.sendResponseHeaders(status, response.length);
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
        GenericHttpClient httpClient = new GenericHttpClient();
        return new ScheduleExecutor(
                scheduleRepository,
                scheduleParameterRepository,
                targetDbConfigRepository,
                storedProcedureInvoker,
                Map.of(
                        ActionType.API_CALL, new ApiCallActionHandler(httpClient, credentialRepository),
                        ActionType.DISPLAY_ONLY, new DisplayOnlyActionHandler()
                ),
                sink,
                displayOutputStore
        );
    }

    @Test
    void storedProcedureFailureProducesFailureResultAndRecordsIt() {
        ScheduleDefinition schedule = displayOnlySchedule();
        when(scheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        when(storedProcedureInvoker.invoke(any(), any(), any(), any(), any()))
                .thenThrow(new StoredProcedureExecutionException("Failed to execute stored procedure SP_SAMPLE: timeout", null));

        ExecutionResult result = newExecutor().execute(SCHEDULE_ID, TriggerType.MANUAL);

        assertThat(result.status()).isEqualTo(ExecutionStatus.FAILURE);
        assertThat(result.errorMessage()).contains("SP_SAMPLE");
        assertThat(sink.recorded()).containsExactly(result);
    }

    @Test
    void apiCallSchedulePerRowAllSucceed() {
        responseStatusesInOrder = new int[]{200, 200};
        when(credentialRepository.findByName("SAMPLE_API_KEY")).thenReturn(Optional.of(
                new Credential(1L, "SAMPLE_API_KEY", "super-secret-api-key-value", null, null, null)
        ));

        ScheduleDefinition schedule = apiCallSchedule(GroupingMode.PER_ROW, null);
        when(scheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        when(storedProcedureInvoker.invoke(any(), any(), any(), any(), any())).thenReturn(List.of(
                Map.of("EMPLOYEE_NO", "E001"),
                Map.of("EMPLOYEE_NO", "E002")
        ));

        ExecutionResult result = newExecutor().execute(SCHEDULE_ID, TriggerType.SCHEDULED);

        assertThat(result.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(result.summary()).contains("2/2 성공");
        assertThat(receivedBodies).hasSize(2);
        assertThat(receivedBodies.get(0)).contains("E001");
        assertThat(receivedBodies.get(1)).contains("E002");
        // the decrypted credential value must never leak into the result
        assertThat(result.summary()).doesNotContain("super-secret-api-key-value");
        assertThat(String.valueOf(result.errorMessage())).doesNotContain("super-secret-api-key-value");
    }

    @Test
    void apiCallScheduleGroupByReportsPartialFailure() {
        responseStatusesInOrder = new int[]{200, 500};
        when(credentialRepository.findByName("SAMPLE_API_KEY")).thenReturn(Optional.of(
                new Credential(1L, "SAMPLE_API_KEY", "super-secret-api-key-value", null, null, null)
        ));

        ScheduleDefinition schedule = apiCallSchedule(GroupingMode.GROUP_BY, "EA_TITLE");
        when(scheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        when(storedProcedureInvoker.invoke(any(), any(), any(), any(), any())).thenReturn(List.of(
                Map.of("EMPLOYEE_NO", "E001", "EA_TITLE", "Title A"),
                Map.of("EMPLOYEE_NO", "E002", "EA_TITLE", "Title B")
        ));

        ExecutionResult result = newExecutor().execute(SCHEDULE_ID, TriggerType.SCHEDULED);

        assertThat(result.status()).isEqualTo(ExecutionStatus.FAILURE);
        assertThat(result.summary()).contains("1/2 성공");
        assertThat(result.errorMessage()).contains("HTTP 500");
    }

    @Test
    void displayOnlyScheduleSucceedsAndPopulatesDisplayOutputStore() {
        ScheduleDefinition schedule = displayOnlySchedule();
        when(scheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        when(storedProcedureInvoker.invoke(any(), any(), any(), any(), any())).thenReturn(List.of(
                orderedRow("EMPLOYEE_NO", "E001", "EA_TITLE", "Title A", "EXE_SEQ", 1),
                orderedRow("EMPLOYEE_NO", "E002", "EA_TITLE", "Title B", "EXE_SEQ", 2)
        ));

        ExecutionResult result = newExecutor().execute(SCHEDULE_ID, TriggerType.MANUAL);

        assertThat(result.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(result.summary()).contains("2행");

        DisplayOutput output = displayOutputStore.get(SCHEDULE_ID).orElseThrow();
        assertThat(output.columns()).containsExactly("EMPLOYEE_NO", "EA_TITLE", "EXE_SEQ");
        assertThat(output.rows()).hasSize(2);
    }

    @Test
    void sinkImplementationCanBeSwappedWithoutChangingExecutor() {
        ScheduleDefinition schedule = displayOnlySchedule();
        when(scheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        when(storedProcedureInvoker.invoke(any(), any(), any(), any(), any())).thenReturn(List.of());

        FakeExecutionResultSink alternateSink = new FakeExecutionResultSink();
        ScheduleExecutor executor = new ScheduleExecutor(
                scheduleRepository, scheduleParameterRepository, targetDbConfigRepository,
                storedProcedureInvoker,
                Map.of(ActionType.DISPLAY_ONLY, new DisplayOnlyActionHandler()),
                alternateSink, displayOutputStore
        );

        executor.execute(SCHEDULE_ID, TriggerType.MANUAL);

        assertThat(alternateSink.recorded()).hasSize(1);
        assertThat(sink.recorded()).isEmpty();
    }

    @Test
    void concurrentRunOfSameScheduleIsRejected() throws InterruptedException {
        java.util.concurrent.CountDownLatch invokerEntered = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch releaseInvoker = new java.util.concurrent.CountDownLatch(1);

        when(scheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(displayOnlySchedule()));
        when(storedProcedureInvoker.invoke(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            invokerEntered.countDown();
            releaseInvoker.await();
            return List.of();
        });

        ScheduleExecutor executor = newExecutor();
        Thread firstRun = new Thread(() -> executor.execute(SCHEDULE_ID, TriggerType.SCHEDULED));
        firstRun.start();
        invokerEntered.await();

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                ScheduleAlreadyRunningException.class,
                () -> executor.execute(SCHEDULE_ID, TriggerType.MANUAL)
        )).hasMessageContaining(String.valueOf(SCHEDULE_ID));

        releaseInvoker.countDown();
        firstRun.join();
    }

    /** Map.of() doesn't preserve insertion order; the real StoredProcedureInvoker returns
     *  LinkedHashMap rows in JDBC column order, so fixtures here must match that shape. */
    private Map<String, Object> orderedRow(Object... keyValuePairs) {
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            row.put((String) keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return row;
    }

    private ScheduleDefinition displayOnlySchedule() {
        return new ScheduleDefinition(
                SCHEDULE_ID, "Display only test", true, "0 0 9 * * MON", "Asia/Seoul",
                "SP_SAMPLE", TARGET_DB_CONFIG_ID, ActionType.DISPLAY_ONLY,
                ActionConfigJson.write(new DisplayOnlyActionConfig(null, null)),
                null, null, null, "tester"
        );
    }

    private ScheduleDefinition apiCallSchedule(GroupingMode groupingMode, String groupByField) {
        String bodyTemplate = groupingMode == GroupingMode.GROUP_BY
                ? "{\"empList\":[\"${EMPLOYEE_NO}\"],\"title\":\"${EA_TITLE}\"}"
                : "{\"empList\":[\"${EMPLOYEE_NO}\"]}";

        ApiCallActionConfig config = new ApiCallActionConfig(
                "POST", pushUrl(),
                Map.of("X-Api-Key", "${cred:SAMPLE_API_KEY}"),
                groupingMode, groupByField, bodyTemplate
        );
        return new ScheduleDefinition(
                SCHEDULE_ID, "API call test", true, "0 0/30 * * * MON-FRI", "Asia/Seoul",
                "SP_SAMPLE", TARGET_DB_CONFIG_ID, ActionType.API_CALL,
                ActionConfigJson.write(config),
                null, null, null, "tester"
        );
    }
}
