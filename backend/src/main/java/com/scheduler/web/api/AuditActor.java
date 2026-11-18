package com.scheduler.web.api;

import org.springframework.security.core.Authentication;

/**
 * updated_by audit columns (schedule_definition/credential) are nullable, but when
 * AUTH_DISABLED is on, SecurityConfig skips JwtAuthenticationFilter entirely so
 * SecurityContextHolder never gets populated and the Authentication method-argument resolves
 * to null (not an anonymous/empty Authentication) - calling authentication.getName() directly
 * crashed every write endpoint with an NPE under that local-dev-only flag (reported by a user
 * hitting POST /api/schedules with AUTH_DISABLED=true).
 *
 * "AUTH_DISABLED" is used instead of null so the audit trail still records *that* a write
 * happened with auth off, rather than leaving a blank - it's deliberately
 * ALL-CAPS/reserved-looking so it's never mistaken for a real admin username (real usernames
 * are ordinary identifiers, never this exact reserved string).
 */
final class AuditActor {

    static final String AUTH_DISABLED_PLACEHOLDER = "AUTH_DISABLED";

    private AuditActor() {
    }

    static String nameOrPlaceholder(Authentication authentication) {
        return authentication != null ? authentication.getName() : AUTH_DISABLED_PLACEHOLDER;
    }
}
