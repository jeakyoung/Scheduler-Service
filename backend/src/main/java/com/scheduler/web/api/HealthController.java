package com.scheduler.web.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Minimal liveness endpoint used to verify the frontend dev server can reach the backend
 * across origins (CORS) before any real schedule/auth endpoints exist.
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
