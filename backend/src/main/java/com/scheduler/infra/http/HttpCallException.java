package com.scheduler.infra.http;

public class HttpCallException extends RuntimeException {
    public HttpCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
