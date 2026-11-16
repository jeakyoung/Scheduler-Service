package com.scheduler.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Stateless JWT auth (PLAN.md §6/§10 - decided 2026-11-13): no HTTP session, no CSRF token
 * (there's no session/cookie for CSRF to ride on). /api/auth/login and the health check are
 * the only unauthenticated endpoints; everything else under /api/** requires a valid
 * Authorization: Bearer token, checked by JwtAuthenticationFilter before Spring Security's
 * own authentication filter runs.
 *
 * app.auth.disabled (AUTH_DISABLED env var) is a LOCAL-DEV-ONLY escape hatch requested for
 * easier manual testing - opt-in, defaults to false so production behavior is unchanged
 * unless someone explicitly flips it.
 */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            @Value("${app.auth.disabled}") boolean authDisabled
    ) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Without an explicit entry point, Spring Security falls back to
            // Http403ForbiddenEntryPoint for unauthenticated requests (there's no
            // formLogin/httpBasic configured to imply one) - PLAN.md §10's DoD wants a plain
            // 401 for "no/invalid token", not 403.
            .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));

        if (authDisabled) {
            log.warn("경고: 인증이 비활성화되어 있습니다(AUTH_DISABLED=true). 로컬 개발 전용 설정입니다 - 운영 환경에서는 절대 사용하지 마세요.");
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        } else {
            http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/login", "/api/health").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }
}
