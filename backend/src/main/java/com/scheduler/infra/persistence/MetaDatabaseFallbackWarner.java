package com.scheduler.infra.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * LOCAL DEV ONLY fallback: when DB_URL is unset, application.yml defaults
 * spring.datasource.url to a local H2 file DB so `mvn spring-boot:run` works without
 * Docker/SQL Server. This only ever affects the engine's own meta DB - never the target
 * business DB that StoredProcedureInvoker calls into. Loud WARN here so an accidental
 * production run on H2 (DB_URL forgotten) doesn't go unnoticed in the logs.
 */
@Component
public class MetaDatabaseFallbackWarner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MetaDatabaseFallbackWarner.class);

    private final String jdbcUrl;

    public MetaDatabaseFallbackWarner(@Value("${spring.datasource.url}") String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (jdbcUrl != null && jdbcUrl.startsWith("jdbc:h2:")) {
            log.warn("경고: DB_URL이 설정되지 않아 로컬 개발용 H2 임베디드 DB로 fallback합니다. " +
                    "운영 환경에서는 반드시 DB_URL을 설정하세요.");
        }
    }
}
