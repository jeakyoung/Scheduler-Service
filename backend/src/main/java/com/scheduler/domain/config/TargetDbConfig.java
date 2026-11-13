package com.scheduler.domain.config;

import java.time.Instant;

/**
 * Target business DB (where SPs live) is fixed to MSSQL - no db_type field (PLAN.md §1/§4.3).
 * encryptedPassword holds ciphertext; CredentialCipher (T5) encrypts/decrypts at the
 * repository boundary, not here.
 */
public record TargetDbConfig(
        Long id,
        String name,
        String jdbcUrl,
        String username,
        String encryptedPassword,
        Instant createdAt,
        Instant updatedAt
) {
}
