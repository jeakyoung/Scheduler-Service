package com.scheduler.domain.config;

import java.time.Instant;

/**
 * Target business DB (where SPs live) is fixed to MSSQL - no db_type field (PLAN.md §1/§4.3).
 * `password` is plaintext at this domain boundary - JdbcTargetDbConfigRepository (via
 * CredentialCipher, T5) encrypts it before writing to the encrypted_password column and
 * decrypts it when reading rows back, so callers never have to think about ciphertext.
 */
public record TargetDbConfig(
        Long id,
        String name,
        String jdbcUrl,
        String username,
        String password,
        Instant createdAt,
        Instant updatedAt
) {
}
