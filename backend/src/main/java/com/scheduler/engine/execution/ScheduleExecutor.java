package com.scheduler.engine.execution;

import com.scheduler.domain.config.TargetDbConfig;
import com.scheduler.domain.config.TargetDbConfigRepository;
import com.scheduler.domain.schedule.ActionType;
import com.scheduler.domain.schedule.ScheduleDefinition;
import com.scheduler.domain.schedule.ScheduleParameter;
import com.scheduler.domain.schedule.ScheduleParameterRepository;
import com.scheduler.domain.schedule.ScheduleRepository;
import com.scheduler.domain.schedule.ScheduleTargetDbConfigRepository;
import com.scheduler.engine.action.ActionHandler;
import com.scheduler.engine.action.ActionOutcome;
import com.scheduler.engine.sp.SpParameterResolver;
import com.scheduler.engine.sp.StoredProcedureInvoker;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single execution entry point shared by the Quartz job and the manual run-now API
 * (PLAN.md §2/§8 - this is the generalized equivalent of the legacy Task.run() body).
 * Depends only on the domain repository interfaces and the ExecutionResultSink/
 * DisplayOutputStore ports - never on a concrete persistence implementation, so swapping
 * ExecutionResultSink implementations requires no change here (PLAN.md §3.2's completion
 * criterion for this round).
 */
@Component
public class ScheduleExecutor {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleParameterRepository scheduleParameterRepository;
    private final ScheduleTargetDbConfigRepository scheduleTargetDbConfigRepository;
    private final TargetDbConfigRepository targetDbConfigRepository;
    private final StoredProcedureInvoker storedProcedureInvoker;
    private final Map<ActionType, ActionHandler> actionHandlersByType;
    private final ExecutionResultSink executionResultSink;
    private final DisplayOutputStore displayOutputStore;
    private final LastExecutionResultStore lastExecutionResultStore;

    private final Map<Long, Boolean> runningScheduleIds = new ConcurrentHashMap<>();

    public ScheduleExecutor(
            ScheduleRepository scheduleRepository,
            ScheduleParameterRepository scheduleParameterRepository,
            ScheduleTargetDbConfigRepository scheduleTargetDbConfigRepository,
            TargetDbConfigRepository targetDbConfigRepository,
            StoredProcedureInvoker storedProcedureInvoker,
            Map<ActionType, ActionHandler> actionHandlersByType,
            ExecutionResultSink executionResultSink,
            DisplayOutputStore displayOutputStore,
            LastExecutionResultStore lastExecutionResultStore
    ) {
        this.scheduleRepository = scheduleRepository;
        this.scheduleParameterRepository = scheduleParameterRepository;
        this.scheduleTargetDbConfigRepository = scheduleTargetDbConfigRepository;
        this.targetDbConfigRepository = targetDbConfigRepository;
        this.storedProcedureInvoker = storedProcedureInvoker;
        this.actionHandlersByType = new EnumMap<>(actionHandlersByType);
        this.executionResultSink = executionResultSink;
        this.displayOutputStore = displayOutputStore;
        this.lastExecutionResultStore = lastExecutionResultStore;
    }

    /** Used by web/api's status endpoint (PLAN.md §6) to report "currently running". */
    public boolean isRunning(long scheduleId) {
        return runningScheduleIds.containsKey(scheduleId);
    }

    /**
     * @throws ScheduleAlreadyRunningException if this schedule is already executing (either
     *         a SCHEDULED run in progress, or a concurrent MANUAL run-now) - callers (the
     *         Quartz job, or the run-now controller) decide how to surface that.
     */
    public ExecutionResult execute(long scheduleId, TriggerType triggerType) {
        if (runningScheduleIds.putIfAbsent(scheduleId, Boolean.TRUE) != null) {
            throw new ScheduleAlreadyRunningException(scheduleId);
        }

        Instant startedAt = Instant.now();
        try {
            ExecutionResult result = doExecute(scheduleId, triggerType, startedAt);
            executionResultSink.record(result);
            lastExecutionResultStore.put(result);
            return result;
        } catch (Exception e) {
            ExecutionResult failure = new ExecutionResult(
                    scheduleId, triggerType, startedAt, Instant.now(),
                    ExecutionStatus.FAILURE, "실행 중 오류 발생", shortMessage(e), List.of()
            );
            executionResultSink.record(failure);
            lastExecutionResultStore.put(failure);
            return failure;
        } finally {
            runningScheduleIds.remove(scheduleId);
        }
    }

    /**
     * Visits every target DB configured for this schedule sequentially, not in parallel
     * (PLAN.md §3.1b - the legacy tasks were sequential too, and this keeps partial-failure
     * ordering simple without thread-pool management). Each target DB is wrapped in its own
     * try/catch so one target DB's failure never stops the others from running (PLAN.md
     * §3.1b's explicit partial-failure-tolerant decision).
     */
    private ExecutionResult doExecute(long scheduleId, TriggerType triggerType, Instant startedAt) {
        ScheduleDefinition schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ScheduleNotFoundException(scheduleId));

        List<ScheduleParameter> parameters = scheduleParameterRepository.findByScheduleId(scheduleId);
        List<Object> boundParams = SpParameterResolver.resolve(parameters);
        List<Long> targetDbConfigIds = scheduleTargetDbConfigRepository.findTargetDbConfigIdsByScheduleId(scheduleId);
        ActionHandler handler = actionHandlersByType.get(schedule.actionType());

        List<TargetDbExecutionResult> targetDbResults = new ArrayList<>();
        for (long targetDbConfigId : targetDbConfigIds) {
            targetDbResults.add(executeForTargetDb(schedule, targetDbConfigId, boundParams, handler));
        }

        return new ExecutionResult(
                scheduleId, triggerType, startedAt, Instant.now(),
                aggregateStatus(targetDbResults), summarize(targetDbResults), null, targetDbResults
        );
    }

    private TargetDbExecutionResult executeForTargetDb(
            ScheduleDefinition schedule, long targetDbConfigId, List<Object> boundParams, ActionHandler handler
    ) {
        // Looked up once (REVIEW.md N5 - this used to query by the same id twice: once for the
        // name, once more inside the try block for the existence check/jdbcUrl).
        Optional<TargetDbConfig> targetDbConfigLookup = targetDbConfigRepository.findById(targetDbConfigId);
        String targetDbConfigName = targetDbConfigLookup.map(TargetDbConfig::name).orElse("target-db-" + targetDbConfigId);
        try {
            TargetDbConfig targetDb = targetDbConfigLookup
                    .orElseThrow(() -> new NoSuchElementException("target_db_config " + targetDbConfigId + " not found"));

            List<Map<String, Object>> spResultRows = storedProcedureInvoker.invoke(
                    targetDb.jdbcUrl(), targetDb.username(), targetDb.password(), schedule.spName(), boundParams
            );

            ActionOutcome outcome = handler.handle(schedule, spResultRows);

            if (outcome.displayOutput() != null) {
                displayOutputStore.put(outcome.displayOutput().withTarget(targetDbConfigId, targetDbConfigName));
            }

            ExecutionStatus status = outcome.success() ? ExecutionStatus.SUCCESS : ExecutionStatus.FAILURE;
            return new TargetDbExecutionResult(targetDbConfigId, targetDbConfigName, status, outcome.summary(), outcome.errorMessage());
        } catch (Exception e) {
            return new TargetDbExecutionResult(
                    targetDbConfigId, targetDbConfigName, ExecutionStatus.FAILURE, "대상DB 실행 중 오류 발생", shortMessage(e)
            );
        }
    }

    private static ExecutionStatus aggregateStatus(List<TargetDbExecutionResult> results) {
        if (results.isEmpty()) {
            return ExecutionStatus.FAILURE;
        }
        long successCount = results.stream().filter(r -> r.status() == ExecutionStatus.SUCCESS).count();
        if (successCount == results.size()) {
            return ExecutionStatus.SUCCESS;
        }
        if (successCount == 0) {
            return ExecutionStatus.FAILURE;
        }
        return ExecutionStatus.PARTIAL_SUCCESS;
    }

    private static String summarize(List<TargetDbExecutionResult> results) {
        if (results.isEmpty()) {
            return "선택된 대상DB가 없음";
        }
        long successCount = results.stream().filter(r -> r.status() == ExecutionStatus.SUCCESS).count();
        long failureCount = results.size() - successCount;
        if (failureCount == 0) {
            return "대상DB %d개 중 %d개 성공".formatted(results.size(), successCount);
        }
        return "대상DB %d개 중 %d개 성공, %d개 실패".formatted(results.size(), successCount, failureCount);
    }

    private static String shortMessage(Exception e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : message;
    }
}
