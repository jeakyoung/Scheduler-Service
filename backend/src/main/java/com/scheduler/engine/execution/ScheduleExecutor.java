package com.scheduler.engine.execution;

import com.scheduler.domain.config.TargetDbConfig;
import com.scheduler.domain.config.TargetDbConfigRepository;
import com.scheduler.domain.schedule.ActionType;
import com.scheduler.domain.schedule.ScheduleDefinition;
import com.scheduler.domain.schedule.ScheduleParameter;
import com.scheduler.domain.schedule.ScheduleParameterRepository;
import com.scheduler.domain.schedule.ScheduleRepository;
import com.scheduler.engine.action.ActionHandler;
import com.scheduler.engine.action.ActionOutcome;
import com.scheduler.engine.sp.SpParameterResolver;
import com.scheduler.engine.sp.StoredProcedureInvoker;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
    private final TargetDbConfigRepository targetDbConfigRepository;
    private final StoredProcedureInvoker storedProcedureInvoker;
    private final Map<ActionType, ActionHandler> actionHandlersByType;
    private final ExecutionResultSink executionResultSink;
    private final DisplayOutputStore displayOutputStore;

    private final Map<Long, Boolean> runningScheduleIds = new ConcurrentHashMap<>();

    public ScheduleExecutor(
            ScheduleRepository scheduleRepository,
            ScheduleParameterRepository scheduleParameterRepository,
            TargetDbConfigRepository targetDbConfigRepository,
            StoredProcedureInvoker storedProcedureInvoker,
            Map<ActionType, ActionHandler> actionHandlersByType,
            ExecutionResultSink executionResultSink,
            DisplayOutputStore displayOutputStore
    ) {
        this.scheduleRepository = scheduleRepository;
        this.scheduleParameterRepository = scheduleParameterRepository;
        this.targetDbConfigRepository = targetDbConfigRepository;
        this.storedProcedureInvoker = storedProcedureInvoker;
        this.actionHandlersByType = new EnumMap<>(actionHandlersByType);
        this.executionResultSink = executionResultSink;
        this.displayOutputStore = displayOutputStore;
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
            return result;
        } catch (Exception e) {
            ExecutionResult failure = new ExecutionResult(
                    scheduleId, triggerType, startedAt, Instant.now(),
                    ExecutionStatus.FAILURE, "실행 중 오류 발생", shortMessage(e)
            );
            executionResultSink.record(failure);
            return failure;
        } finally {
            runningScheduleIds.remove(scheduleId);
        }
    }

    private ExecutionResult doExecute(long scheduleId, TriggerType triggerType, Instant startedAt) {
        ScheduleDefinition schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ScheduleNotFoundException(scheduleId));

        List<ScheduleParameter> parameters = scheduleParameterRepository.findByScheduleId(scheduleId);
        List<Object> boundParams = SpParameterResolver.resolve(parameters);

        TargetDbConfig targetDb = targetDbConfigRepository.findById(schedule.targetDbConfigId())
                .orElseThrow(() -> new ScheduleNotFoundException(schedule.targetDbConfigId()));

        List<Map<String, Object>> spResultRows = storedProcedureInvoker.invoke(
                targetDb.jdbcUrl(), targetDb.username(), targetDb.password(), schedule.spName(), boundParams
        );

        ActionHandler handler = actionHandlersByType.get(schedule.actionType());
        ActionOutcome outcome = handler.handle(schedule, spResultRows);

        if (outcome.displayOutput() != null) {
            displayOutputStore.put(outcome.displayOutput());
        }

        ExecutionStatus status = outcome.success() ? ExecutionStatus.SUCCESS : ExecutionStatus.FAILURE;
        return new ExecutionResult(scheduleId, triggerType, startedAt, Instant.now(), status,
                outcome.summary(), outcome.errorMessage());
    }

    private static String shortMessage(Exception e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : message;
    }
}
