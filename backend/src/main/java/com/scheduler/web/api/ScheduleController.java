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
import org.springframework.transaction.annotation.Transactional;
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

    /**
     * @Transactional wraps scheduleRepository.save(...) and replaceForSchedule(...) together
     * (REVIEW.md N4) - without it, a failure in replaceForSchedule (e.g. a target_db_config
     * deleted out from under this request) would leave an already-committed schedule row with
     * zero target DBs, violating PLAN.md §4.3b's "at least one target DB" invariant.
     */
    @PostMapping("/api/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ScheduleResponse create(@Valid @RequestBody ScheduleRequest request, Authentication authentication) {
        String actionConfigJson = writeActionConfig(request.actionConfig());
        validateCron(request.cronExpression(), request.timezone());
        SqlIdentifierValidator.validate(request.spName());
        ActionConfigRequestValidator.validate(request.actionType(), actionConfigJson);
        validateTargetDbConfigIds(request.targetDbConfigIds());

        ScheduleDefinition saved = scheduleRepository.save(new ScheduleDefinition(
                null, request.name(), request.enabled(), request.cronExpression(), request.timezone(),
                request.spName(), request.actionType(), actionConfigJson,
                request.description(), null, null, AuditActor.nameOrPlaceholder(authentication)
        ));
        scheduleTargetDbConfigRepository.replaceForSchedule(saved.id(), request.targetDbConfigIds());
        scheduleRegistrar.register(saved);
        return toResponse(saved, List.of());
    }

    @PutMapping("/api/schedules/{id}")
    @Transactional
    public ScheduleResponse update(@PathVariable long id, @Valid @RequestBody ScheduleRequest request, Authentication authentication) {
        requireSchedule(id);
        String actionConfigJson = writeActionConfig(request.actionConfig());
        validateCron(request.cronExpression(), request.timezone());
        SqlIdentifierValidator.validate(request.spName());
        ActionConfigRequestValidator.validate(request.actionType(), actionConfigJson);
        validateTargetDbConfigIds(request.targetDbConfigIds());

        ScheduleDefinition updated = scheduleRepository.update(new ScheduleDefinition(
                id, request.name(), request.enabled(), request.cronExpression(), request.timezone(),
                request.spName(), request.actionType(), actionConfigJson,
                request.description(), null, null, AuditActor.nameOrPlaceholder(authentication)
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

    /**
     * @Transactional (found while testing the one-time-cron fix above): without it,
     * updateEnabled(...) commits enabled=true immediately, and if the subsequent register()
     * then throws (e.g. re-enabling a schedule whose cron+timezone can no longer fire),
     * enabled=true is left permanently stuck in the DB with no working Quartz trigger behind
     * it - worse, the next application restart's ScheduleBootstrapRunner.run() iterates every
     * enabled schedule with a plain forEach (no per-item try/catch) and would crash on that
     * same row, taking the entire app down on every subsequent restart. Wrapping both calls
     * rolls the enabled flag back too when registration fails, matching create()/update()'s
     * existing @Transactional pattern (REVIEW.md N4).
     */
    @PatchMapping("/api/schedules/{id}/enabled")
    @Transactional
    public ScheduleResponse setEnabled(@PathVariable long id, @Valid @RequestBody EnabledRequest request, Authentication authentication) {
        requireSchedule(id);
        scheduleRepository.updateEnabled(id, request.enabled(), AuditActor.nameOrPlaceholder(authentication));
        ScheduleDefinition updated = requireSchedule(id);
        scheduleRegistrar.register(updated);
        return toResponse(updated, scheduleParameterRepository.findByScheduleId(id));
    }

    private ScheduleDefinition requireSchedule(long id) {
        return scheduleRepository.findById(id).orElseThrow(() -> new ScheduleNotFoundException(id));
    }

    /**
     * Syntax validity alone isn't enough - a one-time cron pattern (CronBuilder pattern #6,
     * which pins a specific year) is syntactically valid but will *never* fire once its
     * date/time has passed. Previously that slipped through here, got saved, and only failed
     * later at ScheduleRegistrar.register()'s scheduler.scheduleJob() call with Quartz's
     * "the given trigger ... will never fire" SchedulerException - wrapped as
     * QuartzRegistrationException, which had no ApiExceptionHandler mapping and leaked out as
     * a bare 500 (user-reported bug). Checking getNextValidTimeAfter(now) here, in the same
     * timezone ScheduleRegistrar will actually register with, catches this before anything is
     * saved. The ApiExceptionHandler mapping for QuartzRegistrationException stays in place as
     * a safety net for other paths that re-register an already-stored schedule (e.g. PATCH
     * .../enabled) without going through this check again.
     */
    private void validateCron(String cron, String timezone) {
        if (!CronExpression.isValidExpression(cron)) {
            throw new IllegalArgumentException("Invalid cron expression: " + cron);
        }
        if (nextFireTimeAfterNow(cron, timezone) == null) {
            throw new IllegalArgumentException(
                    "선택한 날짜/시간이 이미 지나서 스케줄이 실행될 수 없습니다. 미래 시각을 선택하세요.");
        }
    }

    private static java.util.Date nextFireTimeAfterNow(String cron, String timezone) {
        try {
            CronExpression cronExpression = new CronExpression(cron);
            cronExpression.setTimeZone(java.util.TimeZone.getTimeZone(java.time.ZoneId.of(timezone)));
            return cronExpression.getNextValidTimeAfter(new java.util.Date());
        } catch (java.text.ParseException e) {
            // isValidExpression(cron) above already parsed this cron successfully with the
            // same rules, so this is unreachable in practice - but a checked ParseException
            // must not escape as an unmapped 500 if it somehow does.
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
