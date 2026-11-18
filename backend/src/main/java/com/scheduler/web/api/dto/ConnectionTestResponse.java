package com.scheduler.web.api.dto;

/** PLAN.md §6 (2026-11-18) - shared response shape for both target-db test-connection endpoints. */
public record ConnectionTestResponse(boolean success, String message) {
}
