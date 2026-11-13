package com.scheduler.web.api.dto;

public record LoginResponse(String token, String username, String role) {
}
