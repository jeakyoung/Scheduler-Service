package com.scheduler.web.api.dto;

import jakarta.validation.constraints.NotBlank;

public record TargetDbConfigRequest(
        @NotBlank String name,
        @NotBlank String jdbcUrl,
        @NotBlank String username,
        @NotBlank String password
) {
}
