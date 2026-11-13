package com.scheduler.security;

import com.scheduler.domain.admin.AdminUser;
import com.scheduler.domain.admin.AdminUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Legacy had no auth at all; this is new (PLAN.md §8/T11). Solves the bootstrap problem: if
 * no admin_user row exists yet, seed exactly one from INITIAL_ADMIN_USERNAME/PASSWORD on
 * startup, so there's a way to log in at all before any admin account exists.
 */
@Component
public class AdminUserBootstrapRunner implements ApplicationRunner {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final String initialUsername;
    private final String initialPassword;

    public AdminUserBootstrapRunner(
            AdminUserRepository adminUserRepository,
            PasswordEncoder passwordEncoder,
            @Value("${scheduler-service.initial-admin.username}") String initialUsername,
            @Value("${scheduler-service.initial-admin.password}") String initialPassword
    ) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.initialUsername = initialUsername;
        this.initialPassword = initialPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminUserRepository.existsAny()) {
            return;
        }
        if (initialUsername == null || initialUsername.isBlank() || initialPassword == null || initialPassword.isBlank()) {
            return;
        }

        adminUserRepository.save(new AdminUser(
                null, initialUsername, passwordEncoder.encode(initialPassword), "ADMIN", null
        ));
    }
}
