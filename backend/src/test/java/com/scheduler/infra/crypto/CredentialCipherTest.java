package com.scheduler.infra.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialCipherTest {

    private static final String VALID_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void decryptReversesEncrypt() {
        CredentialCipher cipher = new CredentialCipher(VALID_KEY);

        String ciphertext = cipher.encrypt("super-secret-value");

        assertThat(ciphertext).isNotEqualTo("super-secret-value");
        assertThat(cipher.decrypt(ciphertext)).isEqualTo("super-secret-value");
    }

    @Test
    void sameInputEncryptsDifferentlyEachTime() {
        CredentialCipher cipher = new CredentialCipher(VALID_KEY);

        String first = cipher.encrypt("same-value");
        String second = cipher.encrypt("same-value");

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("same-value");
        assertThat(cipher.decrypt(second)).isEqualTo("same-value");
    }

    @Test
    void missingKeyFailsFast() {
        assertThatThrownBy(() -> new CredentialCipher(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MASTER_ENCRYPTION_KEY");

        assertThatThrownBy(() -> new CredentialCipher(" "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MASTER_ENCRYPTION_KEY");
    }

    @Test
    void nonBase64KeyFailsFast() {
        assertThatThrownBy(() -> new CredentialCipher("not-valid-base64!!!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base64");
    }

    @Test
    void wrongLengthKeyFailsFast() {
        String tooShort = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new CredentialCipher(tooShort))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
