package com.scheduler.engine.sp;

public class StoredProcedureExecutionException extends RuntimeException {
    public StoredProcedureExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
