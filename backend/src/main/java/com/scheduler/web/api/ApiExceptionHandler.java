package com.scheduler.web.api;

import com.scheduler.engine.execution.ScheduleAlreadyRunningException;
import com.scheduler.engine.execution.ScheduleNotFoundException;
import com.scheduler.engine.quartz.QuartzRegistrationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    @ExceptionHandler(ScheduleNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ScheduleNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(ScheduleAlreadyRunningException.class)
    public ResponseEntity<Map<String, String>> handleConflict(ScheduleAlreadyRunningException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(ResourceInUseException.class)
    public ResponseEntity<Map<String, String>> handleResourceInUse(ResourceInUseException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(BadCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
    }

    /**
     * Thrown by AuthController when AUTH_DISABLED left Authentication null for an endpoint
     * (/api/auth/me, /api/auth/password) whose whole purpose depends on a real identity.
     */
    @ExceptionHandler(InsufficientAuthenticationException.class)
    public ResponseEntity<Map<String, String>> handleInsufficientAuthentication(InsufficientAuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(java.util.NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(java.util.NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    /**
     * ScheduleController's pre-save "will it ever fire" check (ScheduleController.validateCron)
     * catches the common case up front, but this stays as a safety net for any other path that
     * registers a trigger with Quartz directly (e.g. PATCH .../enabled re-registering an
     * already-stored schedule whose cron+timezone combination can no longer fire) - without it,
     * Quartz's "the given trigger ... will never fire" SchedulerException reached the client as
     * a bare 500. Only the cause message is inspected, not the full stack trace, so no sensitive
     * detail is at risk here (unlike TargetDbConnectionTester.sanitize(), there's no credential
     * or connection string anywhere near this exception).
     */
    @ExceptionHandler(QuartzRegistrationException.class)
    public ResponseEntity<Map<String, String>> handleQuartzRegistration(QuartzRegistrationException e) {
        String causeMessage = e.getCause() != null && e.getCause().getMessage() != null ? e.getCause().getMessage() : "";
        String message = causeMessage.contains("will never fire")
                ? "선택한 날짜/시간이 이미 지나서 스케줄이 실행될 수 없습니다. 미래 시각을 선택하세요."
                : "스케줄을 Quartz에 등록하는 중 오류가 발생했습니다: " + e.getMessage();
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
