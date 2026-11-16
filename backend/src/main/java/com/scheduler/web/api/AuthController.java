package com.scheduler.web.api;

import com.scheduler.domain.admin.AdminUser;
import com.scheduler.domain.admin.AdminUserRepository;
import com.scheduler.security.JwtTokenService;
import com.scheduler.web.api.dto.ChangePasswordRequest;
import com.scheduler.web.api.dto.LoginRequest;
import com.scheduler.web.api.dto.LoginResponse;
import com.scheduler.web.api.dto.MeResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** PLAN.md §6 "인증" table. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthController(AdminUserRepository adminUserRepository, PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AdminUser user = adminUserRepository.findByUsername(request.username())
                .filter(u -> passwordEncoder.matches(request.password(), u.passwordHash()))
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        String token = jwtTokenService.generate(user.username(), user.role());
        return new LoginResponse(token, user.username(), user.role());
    }

    /** Stateless auth - there is no server-side session/token to invalidate; the client just discards its token. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse(null);
        return new MeResponse(authentication.getName(), role);
    }

    /**
     * Not in PLAN.md §6's original API table, but §7 (screen list, item 8) calls for a
     * "본인 계정" password-change screen with no corresponding endpoint ever defined - adding
     * it here as the obvious, minimal-scope backend counterpart (§0: single ADMIN role, no
     * complex account management).
     */
    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request, Authentication authentication) {
        AdminUser user = adminUserRepository.findByUsername(authentication.getName())
                .filter(u -> passwordEncoder.matches(request.currentPassword(), u.passwordHash()))
                .orElseThrow(() -> new BadCredentialsException("Current password is incorrect"));

        adminUserRepository.updatePasswordHash(user.username(), passwordEncoder.encode(request.newPassword()));
        return ResponseEntity.ok().build();
    }
}
