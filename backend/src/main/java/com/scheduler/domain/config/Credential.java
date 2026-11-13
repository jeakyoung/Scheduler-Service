package com.scheduler.domain.config;

import java.time.Instant;

public record Credential(
        Long id,
        String name,
        String encryptedValue,
        String description,
        Instant updatedAt,
        String updatedBy
) {
}
