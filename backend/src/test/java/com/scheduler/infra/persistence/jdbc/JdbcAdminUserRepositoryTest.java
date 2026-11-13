package com.scheduler.infra.persistence.jdbc;

import com.scheduler.domain.admin.AdminUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcAdminUserRepositoryTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private JdbcAdminUserRepository repository;

    @Test
    void existsAnyIsTrueOnceAtLeastOneAdminUserIsSaved() {
        // Shared schema across this test class (singleton Testcontainers DB), so we can't
        // assert "false" before inserting - other test methods may have already added rows.
        // What matters for the T11 bootstrap-seed use case is: after saving a user, it's true.
        repository.save(new AdminUser(null, "admin_test_user_" + System.nanoTime(), "bcrypt-hash", "ADMIN", null));

        assertThat(repository.existsAny()).isTrue();
    }

    @Test
    void savesAndReloadsByUsername() {
        repository.save(new AdminUser(null, "jdoe", "bcrypt-hash-value", "ADMIN", null));

        AdminUser reloaded = repository.findByUsername("jdoe").orElseThrow();
        assertThat(reloaded.passwordHash()).isEqualTo("bcrypt-hash-value");
        assertThat(reloaded.role()).isEqualTo("ADMIN");
        assertThat(reloaded.createdAt()).isNotNull();
    }

    @Test
    void findByUsernameIsEmptyWhenMissing() {
        assertThat(repository.findByUsername("does-not-exist")).isEmpty();
    }
}
