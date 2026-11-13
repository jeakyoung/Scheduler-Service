package com.scheduler.engine.quartz;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MSSQLServerContainer;

/**
 * Same Testcontainers singleton-container approach as
 * com.scheduler.infra.persistence.jdbc.AbstractRepositoryIntegrationTest, but with Quartz
 * auto-startup left at its default (true) since these tests exercise the real Scheduler -
 * a separate base (rather than overriding the other one's @DynamicPropertySource) avoids
 * relying on cross-class static-method override ordering.
 */
@SpringBootTest
public abstract class AbstractQuartzIntegrationTest {

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
        registry.add("MASTER_ENCRYPTION_KEY", () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    }
}
