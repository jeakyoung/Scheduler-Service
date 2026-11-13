package com.scheduler.domain.config;

import java.time.Instant;

/**
 * `value` is plaintext at this domain boundary - JdbcCredentialRepository (via
 * CredentialCipher, T5) encrypts it before writing to the encrypted_value column and
 * decrypts it when reading rows back.
 */
public record Credential(
        Long id,
        String name,
        String value,
        String description,
        Instant updatedAt,
        String updatedBy
) {
}
