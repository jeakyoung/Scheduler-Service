package com.scheduler.infra.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM encryption for target_db_config.encrypted_password / credential.encrypted_value
 * at rest (PLAN.md §4.7). The master key comes from the MASTER_ENCRYPTION_KEY env var
 * (base64, 32 bytes) - never from the meta DB itself, so a DB dump alone can't decrypt
 * anything. Missing/invalid key fails fast at startup rather than letting secrets flow as
 * plaintext.
 */
@Component
public class CredentialCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int REQUIRED_KEY_LENGTH_BYTES = 32;

    private final SecretKeySpec key;

    public CredentialCipher(@Value("${scheduler-service.master-encryption-key}") String base64Key) {
        this.key = loadKey(base64Key);
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer combined = ByteBuffer.allocate(iv.length + cipherText.length);
            combined.put(iv).put(cipherText);
            return Base64.getEncoder().encodeToString(combined.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt value", e);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH_BYTES);
            byte[] cipherText = Arrays.copyOfRange(combined, GCM_IV_LENGTH_BYTES, combined.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plain = cipher.doFinal(cipherText);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to decrypt value", e);
        }
    }

    private static SecretKeySpec loadKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "MASTER_ENCRYPTION_KEY is not set. Generate one with e.g. `openssl rand -base64 32` " +
                            "and set it as an environment variable before starting the application.");
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("MASTER_ENCRYPTION_KEY must be valid base64", e);
        }

        if (keyBytes.length != REQUIRED_KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "MASTER_ENCRYPTION_KEY must decode to exactly " + REQUIRED_KEY_LENGTH_BYTES +
                            " bytes (AES-256), but decoded to " + keyBytes.length + " bytes");
        }

        return new SecretKeySpec(keyBytes, "AES");
    }
}
