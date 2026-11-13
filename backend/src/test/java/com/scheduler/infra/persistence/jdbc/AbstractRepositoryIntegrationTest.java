package com.scheduler.infra.persistence.jdbc;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MSSQLServerContainer;

/**
 * Boots the full Spring context (Flyway migrations included) against a throwaway
 * MSSQLServerContainer, so repository tests exercise the real V1/V2 schema rather than a
 * hand-rolled in-memory substitute. Quartz auto-startup is disabled - these tests are about
 * repositories, not the scheduler (that's T7's concern).
 *
 * Deliberately NOT using @Testcontainers/@Container: since MSSQL is a static field declared
 * on this *abstract* base, that field is one single piece of static storage shared by every
 * subclass (Java doesn't duplicate inherited static fields per subclass) - mixing that with
 * the @Testcontainers JUnit5 extension's per-class start/stop hooks caused the container to
 * be torn down and/or raced against Spring context setup between test classes, producing
 * flaky "connection refused" failures. Instead this uses Testcontainers' documented "singleton
 * container" pattern: start it once in a static initializer and let it live for the whole JVM
 * (Testcontainers' Ryuk reaper still cleans it up afterwards regardless of annotations).
 */
@SpringBootTest
public abstract class AbstractRepositoryIntegrationTest {

    static final MSSQLServerContainer<?> MSSQL = new MSSQLServerContainer<>(
            "mcr.microsoft.com/mssql/server:2022-latest")
            .acceptLicense();

    static {
        MSSQL.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", () -> MSSQL.getJdbcUrl() + ";trustServerCertificate=true;");
        registry.add("DB_USERNAME", MSSQL::getUsername);
        registry.add("DB_PASSWORD", MSSQL::getPassword);
        // 32 zero bytes, base64-encoded - a valid AES-256 key shape for tests only (CredentialCipher
        // requires exactly 32 decoded bytes; this is not used for anything beyond throwaway test data).
        registry.add("MASTER_ENCRYPTION_KEY", () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        registry.add("spring.quartz.auto-startup", () -> "false");
    }
}
