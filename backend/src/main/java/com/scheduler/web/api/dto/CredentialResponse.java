package com.scheduler.web.api.dto;

import java.time.Instant;

public record CredentialResponse(
        long id,
        String name,
        String maskedValue,
        String description,
        Instant updatedAt,
        String updatedBy
) {
}
