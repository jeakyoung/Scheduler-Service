package com.scheduler.web.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CredentialRequest(
        @NotBlank String name,
        @NotBlank String value,
        String description
) {
}
