package com.scheduler.web.api;

import com.scheduler.domain.schedule.ScheduleRepository;
import com.scheduler.engine.execution.DisplayOutput;
import com.scheduler.engine.execution.DisplayOutputStore;
import com.scheduler.engine.execution.ExecutionResult;
import com.scheduler.engine.execution.ExecutionStatus;
import com.scheduler.engine.execution.LastExecutionResultStore;
import com.scheduler.engine.execution.ScheduleExecutor;
import com.scheduler.engine.execution.ScheduleNotFoundException;
import com.scheduler.engine.execution.TriggerType;
import com.scheduler.engine.quartz.ScheduleRegistrar;
import com.scheduler.web.api.dto.RunResultResponse;
import com.scheduler.web.api.dto.ScheduleStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** PLAN.md §6 "실행" table - manual run-now + last-result + status. */
@RestController
public class ScheduleExecutionController {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleExecutor scheduleExecutor;
    private final ScheduleRegistrar scheduleRegistrar;
    private final LastExecutionResultStore lastExecutionResultStore;
    private final DisplayOutputStore displayOutputStore;

    public ScheduleExecutionController(
            ScheduleRepository scheduleRepository,
            ScheduleExecutor scheduleExecutor,
            ScheduleRegistrar scheduleRegistrar,
            LastExecutionResultStore lastExecutionResultStore,
            DisplayOutputStore displayOutputStore
    ) {
        this.scheduleRepository = scheduleRepository;
        this.scheduleExecutor = scheduleExecutor;
        this.scheduleRegistrar = scheduleRegistrar;
        this.lastExecutionResultStore = lastExecutionResultStore;
        this.displayOutputStore = displayOutputStore;
    }

    /**
     * Runs the schedule once, synchronously, independent of its regular Quartz trigger.
     * ScheduleAlreadyRunningException (e.g. a SCHEDULED run is already in progress) bubbles
     * up to ApiExceptionHandler, which maps it to 409.
     */
    @PostMapping("/api/schedules/{id}/run-now")
    public RunResultResponse runNow(@PathVariable long id) {
        requireSchedule(id);
        ExecutionResult result = scheduleExecutor.execute(id, TriggerType.MANUAL);
        return new RunResultResponse(result, displayOutputFor(id, result));
    }

    @GetMapping("/api/schedules/{id}/last-result")
    public ResponseEntity<RunResultResponse> lastResult(@PathVariable long id) {
        requireSchedule(id);
        return lastExecutionResultStore.get(id)
                .map(result -> ResponseEntity.ok(new RunResultResponse(result, displayOutputFor(id, result))))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * DisplayOutputStore only ever holds the latest *successful* DISPLAY_ONLY run - it is
     * never cleared on a later failure. Without this check, run-now/last-result for a
     * schedule whose current execution failed would still attach a stale displayOutput from
     * some earlier successful run (REVIEW.md N3), which looks like "this failure's data".
     */
    private DisplayOutput displayOutputFor(long id, ExecutionResult result) {
        if (result.status() != ExecutionStatus.SUCCESS) {
            return null;
        }
        return displayOutputStore.get(id).orElse(null);
    }

    @GetMapping("/api/schedules/{id}/status")
    public ScheduleStatusResponse status(@PathVariable long id) {
        requireSchedule(id);
        var nextFireTime = scheduleRegistrar.getNextFireTime(id).map(java.util.Date::toInstant).orElse(null);
        return new ScheduleStatusResponse(id, nextFireTime, scheduleExecutor.isRunning(id));
    }

    private void requireSchedule(long id) {
        scheduleRepository.findById(id).orElseThrow(() -> new ScheduleNotFoundException(id));
    }
}
