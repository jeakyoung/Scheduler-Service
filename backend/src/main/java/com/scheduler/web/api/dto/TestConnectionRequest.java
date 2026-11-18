package com.scheduler.web.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Body for POST /api/config/target-db/test-connection - plaintext, never persisted. */
public record TestConnectionRequest(
        @NotBlank String jdbcUrl,
        @NotBlank String username,
        @NotBlank String password
) {
}
