package com.scheduler.infra.persistence.jdbc;

import com.scheduler.domain.config.Credential;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcCredentialRepositoryTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private JdbcCredentialRepository repository;

    @Test
    void savesAndReloadsByIdAndName() {
        Credential saved = repository.save(new Credential(
                null, "SAMPLE_API_KEY", "ciphertext-value", "example credential for tests", null, "tester"
        ));

        Credential byId = repository.findById(saved.id()).orElseThrow();
        assertThat(byId.name()).isEqualTo("SAMPLE_API_KEY");
        assertThat(byId.encryptedValue()).isEqualTo("ciphertext-value");
        assertThat(byId.description()).isEqualTo("example credential for tests");
        assertThat(byId.updatedBy()).isEqualTo("tester");
        assertThat(byId.updatedAt()).isNotNull();

        Credential byName = repository.findByName("SAMPLE_API_KEY").orElseThrow();
        assertThat(byName.id()).isEqualTo(saved.id());
    }

    @Test
    void updateReplacesValue() {
        Credential saved = repository.save(new Credential(
                null, "ROTATING_KEY", "old-ciphertext", null, null, "tester"
        ));

        Credential updated = repository.update(new Credential(
                saved.id(), "ROTATING_KEY", "new-ciphertext", "rotated", null, "tester2"
        ));

        assertThat(updated.encryptedValue()).isEqualTo("new-ciphertext");
        assertThat(updated.description()).isEqualTo("rotated");
        assertThat(updated.updatedBy()).isEqualTo("tester2");
    }

    @Test
    void deleteRemovesRow() {
        Credential saved = repository.save(new Credential(
                null, "TO_DELETE", "enc", null, null, "tester"
        ));

        repository.deleteById(saved.id());

        assertThat(repository.findById(saved.id())).isEmpty();
    }
}
