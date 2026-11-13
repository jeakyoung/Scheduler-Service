package com.scheduler.engine.quartz;

public class QuartzRegistrationException extends RuntimeException {
    public QuartzRegistrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
