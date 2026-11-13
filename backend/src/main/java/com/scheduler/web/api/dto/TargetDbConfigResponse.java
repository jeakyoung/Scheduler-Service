package com.scheduler.web.api.dto;

import java.time.Instant;

public record TargetDbConfigResponse(
        long id,
        String name,
        String jdbcUrl,
        String username,
        String maskedPassword,
        Instant createdAt,
        Instant updatedAt
) {
}
