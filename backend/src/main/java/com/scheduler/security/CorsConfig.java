package com.scheduler.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Backend and frontend (React SPA) are independently deployed, so the frontend's
 * origin(s) must be explicitly allow-listed here (app.cors.allowed-origin, env-injected -
 * see PLAN.md §6, can be a comma-separated list for multiple deployed frontend origins).
 * This is registered on the Spring Security filter chain in SecurityConfig so preflight
 * requests are handled before authentication.
 *
 * allowCredentials is NOT set - auth is a stateless JWT sent via the Authorization header
 * (decided 2026-11-13), not a cookie, so there is nothing CORS-credentialed about these
 * requests. Custom headers like Authorization still just need to be in allowedHeaders.
 */
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origin}")
    private String allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
