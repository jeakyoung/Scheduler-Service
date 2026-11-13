package com.scheduler.infra.persistence.jdbc;

import com.scheduler.domain.config.TargetDbConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcTargetDbConfigRepositoryTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private JdbcTargetDbConfigRepository repository;

    @Test
    void savesAndReloadsAllFields() {
        TargetDbConfig saved = repository.save(new TargetDbConfig(
                null,
                "Test ERP",
                "jdbc:sqlserver://example-business-db:1433;databaseName=erp;",
                "erp_user",
                "ciphertext-not-plaintext",
                null,
                null
        ));

        assertThat(saved.id()).isNotNull();

        TargetDbConfig reloaded = repository.findById(saved.id()).orElseThrow();
        assertThat(reloaded.name()).isEqualTo("Test ERP");
        assertThat(reloaded.jdbcUrl()).isEqualTo("jdbc:sqlserver://example-business-db:1433;databaseName=erp;");
        assertThat(reloaded.username()).isEqualTo("erp_user");
        assertThat(reloaded.encryptedPassword()).isEqualTo("ciphertext-not-plaintext");
        assertThat(reloaded.createdAt()).isNotNull();
        assertThat(reloaded.updatedAt()).isNotNull();
    }

    @Test
    void updatesExistingRow() {
        TargetDbConfig saved = repository.save(new TargetDbConfig(
                null, "Initial", "jdbc:sqlserver://a;", "user_a", "enc_a", null, null
        ));

        TargetDbConfig updated = repository.update(new TargetDbConfig(
                saved.id(), "Renamed", "jdbc:sqlserver://b;", "user_b", "enc_b", null, null
        ));

        assertThat(updated.name()).isEqualTo("Renamed");
        assertThat(updated.jdbcUrl()).isEqualTo("jdbc:sqlserver://b;");
        assertThat(updated.username()).isEqualTo("user_b");
        assertThat(updated.encryptedPassword()).isEqualTo("enc_b");
    }

    @Test
    void deletesRow() {
        TargetDbConfig saved = repository.save(new TargetDbConfig(
                null, "ToDelete", "jdbc:sqlserver://c;", "user_c", "enc_c", null, null
        ));

        repository.deleteById(saved.id());

        assertThat(repository.findById(saved.id())).isEmpty();
    }
}
