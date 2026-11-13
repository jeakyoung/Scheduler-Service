package com.scheduler.infra.http;

public record HttpCallResult(int statusCode, String body) {
    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }
}
