package com.scheduler.infra.persistence.jdbc;

import com.scheduler.domain.config.Credential;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcCredentialRepositoryTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private JdbcCredentialRepository repository;

    @Autowired
    private JdbcTemplate plainJdbc;

    @Test
    void savesAndReloadsByIdAndName() {
        Credential saved = repository.save(new Credential(
                null, "SAMPLE_API_KEY", "super-secret-api-key-value", "example credential for tests", null, "tester"
        ));

        Credential byId = repository.findById(saved.id()).orElseThrow();
        assertThat(byId.name()).isEqualTo("SAMPLE_API_KEY");
        assertThat(byId.value()).isEqualTo("super-secret-api-key-value");
        assertThat(byId.description()).isEqualTo("example credential for tests");
        assertThat(byId.updatedBy()).isEqualTo("tester");
        assertThat(byId.updatedAt()).isNotNull();

        Credential byName = repository.findByName("SAMPLE_API_KEY").orElseThrow();
        assertThat(byName.id()).isEqualTo(saved.id());
    }

    @Test
    void valueIsStoredEncryptedAtRest() {
        Credential saved = repository.save(new Credential(
                null, "ENCRYPTED_CHECK_KEY", "plaintext-secret-xyz", null, null, "tester"
        ));

        String rawColumnValue = plainJdbc.queryForObject(
                "SELECT encrypted_value FROM credential WHERE id = ?", String.class, saved.id()
        );

        assertThat(rawColumnValue).isNotEqualTo("plaintext-secret-xyz");
        assertThat(rawColumnValue).doesNotContain("plaintext-secret-xyz");
    }

    @Test
    void updateReplacesValue() {
        Credential saved = repository.save(new Credential(
                null, "ROTATING_KEY", "old-secret-value", null, null, "tester"
        ));

        Credential updated = repository.update(new Credential(
                saved.id(), "ROTATING_KEY", "new-secret-value", "rotated", null, "tester2"
        ));

        assertThat(updated.value()).isEqualTo("new-secret-value");
        assertThat(updated.description()).isEqualTo("rotated");
        assertThat(updated.updatedBy()).isEqualTo("tester2");
    }

    @Test
    void deleteRemovesRow() {
        Credential saved = repository.save(new Credential(
                null, "TO_DELETE", "some-secret", null, null, "tester"
        ));

        repository.deleteById(saved.id());

        assertThat(repository.findById(saved.id())).isEmpty();
    }
}
