package com.scheduler.domain.admin;

import java.time.Instant;

public record AdminUser(
        Long id,
        String username,
        String passwordHash,
        String role,
        Instant createdAt
) {
}
