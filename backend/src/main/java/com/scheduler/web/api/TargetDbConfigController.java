package com.scheduler.web.api;

import com.scheduler.domain.config.TargetDbConfig;
import com.scheduler.domain.config.TargetDbConfigRepository;
import com.scheduler.domain.schedule.ScheduleDefinition;
import com.scheduler.domain.schedule.ScheduleRepository;
import com.scheduler.domain.schedule.ScheduleTargetDbConfigRepository;
import com.scheduler.infra.db.TargetDbConnectionTester;
import com.scheduler.web.api.dto.ConnectionTestResponse;
import com.scheduler.web.api.dto.TargetDbConfigRequest;
import com.scheduler.web.api.dto.TargetDbConfigResponse;
import com.scheduler.web.api.dto.TestConnectionRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** PLAN.md §6 "대상 DB 설정" table. */
@RestController
public class TargetDbConfigController {

    private final TargetDbConfigRepository targetDbConfigRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleTargetDbConfigRepository scheduleTargetDbConfigRepository;
    private final TargetDbConnectionTester connectionTester;

    public TargetDbConfigController(
            TargetDbConfigRepository targetDbConfigRepository,
            ScheduleRepository scheduleRepository,
            ScheduleTargetDbConfigRepository scheduleTargetDbConfigRepository,
            TargetDbConnectionTester connectionTester
    ) {
        this.targetDbConfigRepository = targetDbConfigRepository;
        this.scheduleRepository = scheduleRepository;
        this.scheduleTargetDbConfigRepository = scheduleTargetDbConfigRepository;
        this.connectionTester = connectionTester;
    }

    @GetMapping("/api/config/target-db")
    public List<TargetDbConfigResponse> list() {
        return targetDbConfigRepository.findAll().stream().map(this::toResponse).toList();
    }

    @PostMapping("/api/config/target-db")
    @ResponseStatus(HttpStatus.CREATED)
    public TargetDbConfigResponse create(@Valid @RequestBody TargetDbConfigRequest request) {
        TargetDbConfig saved = targetDbConfigRepository.save(new TargetDbConfig(
                null, request.name(), request.jdbcUrl(), request.username(), request.password(), null, null
        ));
        return toResponse(saved);
    }

    @PutMapping("/api/config/target-db/{id}")
    public TargetDbConfigResponse update(@PathVariable long id, @Valid @RequestBody TargetDbConfigRequest request) {
        requireExists(id);
        TargetDbConfig updated = targetDbConfigRepository.update(new TargetDbConfig(
                id, request.name(), request.jdbcUrl(), request.username(), request.password(), null, null
        ));
        return toResponse(updated);
    }

    @DeleteMapping("/api/config/target-db/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        requireExists(id);

        List<Long> referencingScheduleIds = scheduleTargetDbConfigRepository.findScheduleIdsByTargetDbConfigId(id);
        List<String> referencingSchedules = referencingScheduleIds.stream()
                .flatMap(scheduleId -> scheduleRepository.findById(scheduleId).stream())
                .map(ScheduleDefinition::name)
                .toList();

        if (!referencingSchedules.isEmpty()) {
            throw new ResourceInUseException(
                    "target_db_config " + id + " is still used by schedule(s): " + String.join(", ", referencingSchedules));
        }

        targetDbConfigRepository.deleteById(id);
    }

    /** PLAN.md §6 (2026-11-18) - test unsaved input directly, nothing is persisted. */
    @PostMapping("/api/config/target-db/test-connection")
    public ConnectionTestResponse testConnection(@Valid @RequestBody TestConnectionRequest request) {
        return connectionTester.test(request.jdbcUrl(), request.username(), request.password());
    }

    /** PLAN.md §6 (2026-11-18) - test a saved config; the server decrypts, the client never sees the plaintext. */
    @PostMapping("/api/config/target-db/{id}/test-connection")
    public ConnectionTestResponse testConnectionById(@PathVariable long id) {
        TargetDbConfig config = targetDbConfigRepository.findById(id)
                .orElseThrow(() -> new java.util.NoSuchElementException("target_db_config " + id + " not found"));
        return connectionTester.test(config.jdbcUrl(), config.username(), config.password());
    }

    private void requireExists(long id) {
        if (targetDbConfigRepository.findById(id).isEmpty()) {
            throw new java.util.NoSuchElementException("target_db_config " + id + " not found");
        }
    }

    private TargetDbConfigResponse toResponse(TargetDbConfig config) {
        return new TargetDbConfigResponse(
                config.id(), config.name(), config.jdbcUrl(), config.username(),
                Masking.lastFour(config.password()), config.createdAt(), config.updatedAt()
        );
    }
}
