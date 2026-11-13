package com.scheduler.engine.execution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingExecutionResultSink implements ExecutionResultSink {

    private static final Logger log = LoggerFactory.getLogger(LoggingExecutionResultSink.class);

    @Override
    public void record(ExecutionResult result) {
        if (result.status() == ExecutionStatus.SUCCESS) {
            log.info("Schedule {} ({}) succeeded: {}", result.scheduleId(), result.triggerType(), result.summary());
        } else {
            log.error("Schedule {} ({}) failed: {} - {}", result.scheduleId(), result.triggerType(),
                    result.summary(), result.errorMessage());
        }
    }
}
