package com.scheduler.web.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scheduler.domain.config.TargetDbConfigRepository;
import com.scheduler.domain.schedule.ScheduleDefinition;
import com.scheduler.domain.schedule.ScheduleParameter;
import com.scheduler.domain.schedule.ScheduleParameterRepository;
import com.scheduler.domain.schedule.ScheduleRepository;
import com.scheduler.domain.schedule.ScheduleTargetDbConfigRepository;
import com.scheduler.engine.execution.ScheduleNotFoundException;
import com.scheduler.engine.quartz.ScheduleRegistrar;
import com.scheduler.engine.sp.SqlIdentifierValidator;
import com.scheduler.web.api.dto.EnabledRequest;
import com.scheduler.web.api.dto.ScheduleParameterResponse;
import com.scheduler.web.api.dto.ScheduleRequest;
import com.scheduler.web.api.dto.ScheduleResponse;
import com.scheduler.web.api.dto.TargetDbConfigRef;
import jakarta.validation.Valid;
import org.quartz.CronExpression;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** PLAN.md §6 "스케줄 CRUD + 파라미터" table. */
@RestController
public class ScheduleController {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleParameterRepository scheduleParameterRepository;
    private final ScheduleTargetDbConfigRepository scheduleTargetDbConfigRepository;
    private final TargetDbConfigRepository targetDbConfigRepository;
    private final ScheduleRegistrar scheduleRegistrar;
    private final ObjectMapper objectMapper;

    public ScheduleController(
            ScheduleRepository scheduleRepository,
            ScheduleParameterRepository scheduleParameterRepository,
            ScheduleTargetDbConfigRepository scheduleTargetDbConfigRepository,
            TargetDbConfigRepository targetDbConfigRepository,
            ScheduleRegistrar scheduleRegistrar,
            ObjectMapper objectMapper
    ) {
        this.scheduleRepository = scheduleRepository;
        this.scheduleParameterRepository = scheduleParameterRepository;
        this.scheduleTargetDbConfigRepository = scheduleTargetDbConfigRepository;
        this.targetDbConfigRepository = targetDbConfigRepository;
        this.scheduleRegistrar = scheduleRegistrar;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/api/schedules")
    public List<ScheduleResponse> list() {
        return scheduleRepository.findAll().stream().map(s -> toResponse(s, List.of())).toList();
    }

    @GetMapping("/api/schedules/{id}")
    public ScheduleResponse get(@PathVariable long id) {
        ScheduleDefinition schedule = requireSchedule(id);
        return toResponse(schedule, scheduleParameterRepository.findByScheduleId(id));
    }

    @PostMapping("/api/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleResponse create(@Valid @RequestBody ScheduleRequest request, Authentication authentication) {
        String actionConfigJson = writeActionConfig(request.actionConfig());
        validateCron(request.cronExpression());
        SqlIdentifierValidator.validate(request.spName());
        ActionConfigRequestValidator.validate(request.actionType(), actionConfigJson);
        validateTargetDbConfigIds(request.targetDbConfigIds());

        ScheduleDefinition saved = scheduleRepository.save(new ScheduleDefinition(
                null, request.name(), request.enabled(), request.cronExpression(), request.timezone(),
                request.spName(), request.actionType(), actionConfigJson,
                request.description(), null, null, authentication.getName()
        ));
        scheduleTargetDbConfigRepository.replaceForSchedule(saved.id(), request.targetDbConfigIds());
        scheduleRegistrar.register(saved);
        return toResponse(saved, List.of());
    }

    @PutMapping("/api/schedules/{id}")
    public ScheduleResponse update(@PathVariable long id, @Valid @RequestBody ScheduleRequest request, Authentication authentication) {
        requireSchedule(id);
        String actionConfigJson = writeActionConfig(request.actionConfig());
        validateCron(request.cronExpression());
        SqlIdentifierValidator.validate(request.spName());
        ActionConfigRequestValidator.validate(request.actionType(), actionConfigJson);
        validateTargetDbConfigIds(request.targetDbConfigIds());

        ScheduleDefinition updated = scheduleRepository.update(new ScheduleDefinition(
                id, request.name(), request.enabled(), request.cronExpression(), request.timezone(),
                request.spName(), request.actionType(), actionConfigJson,
                request.description(), null, null, authentication.getName()
        ));
        scheduleTargetDbConfigRepository.replaceForSchedule(id, request.targetDbConfigIds());
        scheduleRegistrar.register(updated);
        return toResponse(updated, scheduleParameterRepository.findByScheduleId(id));
    }

    @DeleteMapping("/api/schedules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        requireSchedule(id);
        scheduleRegistrar.unregister(id);
        scheduleRepository.deleteById(id);
    }

    @PatchMapping("/api/schedules/{id}/enabled")
    public ScheduleResponse setEnabled(@PathVariable long id, @Valid @RequestBody EnabledRequest request, Authentication authentication) {
        requireSchedule(id);
        scheduleRepository.updateEnabled(id, request.enabled(), authentication.getName());
        ScheduleDefinition updated = requireSchedule(id);
        scheduleRegistrar.register(updated);
        return toResponse(updated, scheduleParameterRepository.findByScheduleId(id));
    }

    private ScheduleDefinition requireSchedule(long id) {
        return scheduleRepository.findById(id).orElseThrow(() -> new ScheduleNotFoundException(id));
    }

    private void validateCron(String cron) {
        if (!CronExpression.isValidExpression(cron)) {
            throw new IllegalArgumentException("Invalid cron expression: " + cron);
        }
    }

    /**
     * @NotEmpty on the request DTO already rejects an empty array; this catches the other two
     * 400 cases PLAN.md §6/REVIEW.md M4 call for - a duplicate id, and an id that doesn't
     * exist - before replaceForSchedule would otherwise hit either as a composite-PK/FK
     * violation (500, not 400). Duplicates are rejected outright rather than silently
     * deduplicated (REVIEW.md M4's "don't silently do something else" principle).
     */
    private void validateTargetDbConfigIds(List<Long> targetDbConfigIds) {
        java.util.Set<Long> distinct = new java.util.LinkedHashSet<>(targetDbConfigIds);
        if (distinct.size() < targetDbConfigIds.size()) {
            throw new IllegalArgumentException("target_db_config ids must not contain duplicates: " + targetDbConfigIds);
        }

        List<Long> missing = targetDbConfigIds.stream()
                .filter(id -> targetDbConfigRepository.findById(id).isEmpty())
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("target_db_config id(s) not found: " + missing);
        }
    }

    private String writeActionConfig(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid action_config JSON");
        }
    }

    private ScheduleResponse toResponse(ScheduleDefinition schedule, List<ScheduleParameter> parameters) {
        JsonNode actionConfigNode;
        try {
            actionConfigNode = objectMapper.readTree(schedule.actionConfig());
        } catch (JsonProcessingException e) {
            actionConfigNode = null;
        }

        java.time.Instant nextFireTime = scheduleRegistrar.getNextFireTime(schedule.id())
                .map(java.util.Date::toInstant)
                .orElse(null);

        List<ScheduleParameterResponse> parameterResponses = parameters.stream()
                .map(p -> new ScheduleParameterResponse(p.id(), p.paramOrder(), p.paramName(), p.dataType(), p.valueSource(), p.literalValue()))
                .toList();

        List<Long> targetDbConfigIds = scheduleTargetDbConfigRepository.findTargetDbConfigIdsByScheduleId(schedule.id());
        List<TargetDbConfigRef> targetDbConfigs = targetDbConfigIds.stream()
                .flatMap(id -> targetDbConfigRepository.findById(id).stream())
                .map(config -> new TargetDbConfigRef(config.id(), config.name()))
                .toList();

        return new ScheduleResponse(
                schedule.id(), schedule.name(), schedule.enabled(), schedule.cronExpression(), schedule.timezone(),
                schedule.spName(), targetDbConfigIds, targetDbConfigs, schedule.actionType(), actionConfigNode,
                schedule.description(), schedule.createdAt(), schedule.updatedAt(), schedule.updatedBy(),
                nextFireTime, parameterResponses
        );
    }
}
