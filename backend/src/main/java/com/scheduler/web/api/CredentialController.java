package com.scheduler.web.api;

import com.scheduler.domain.config.Credential;
import com.scheduler.domain.config.CredentialRepository;
import com.scheduler.domain.schedule.ScheduleDefinition;
import com.scheduler.domain.schedule.ScheduleRepository;
import com.scheduler.web.api.dto.CredentialRequest;
import com.scheduler.web.api.dto.CredentialResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

/** PLAN.md §6 "자격증명(Credential)" table. */
@RestController
public class CredentialController {

    private final CredentialRepository credentialRepository;
    private final ScheduleRepository scheduleRepository;

    public CredentialController(CredentialRepository credentialRepository, ScheduleRepository scheduleRepository) {
        this.credentialRepository = credentialRepository;
        this.scheduleRepository = scheduleRepository;
    }

    @GetMapping("/api/config/credentials")
    public List<CredentialResponse> list() {
        return credentialRepository.findAll().stream().map(this::toResponse).toList();
    }

    @PostMapping("/api/config/credentials")
    @ResponseStatus(HttpStatus.CREATED)
    public CredentialResponse create(@Valid @RequestBody CredentialRequest request, Authentication authentication) {
        Credential saved = credentialRepository.save(new Credential(
                null, request.name(), request.value(), request.description(), null, AuditActor.nameOrPlaceholder(authentication)
        ));
        return toResponse(saved);
    }

    @PutMapping("/api/config/credentials/{id}")
    public CredentialResponse update(@PathVariable long id, @Valid @RequestBody CredentialRequest request, Authentication authentication) {
        requireExists(id);
        Credential updated = credentialRepository.update(new Credential(
                id, request.name(), request.value(), request.description(), null, AuditActor.nameOrPlaceholder(authentication)
        ));
        return toResponse(updated);
    }

    /**
     * PLAN.md §6: "삭제(참조 중인 action_config 있으면 경고)" - a warning, not a hard block
     * like target_db_config's delete. By default this returns 409 listing the referencing
     * schedules if any exist; pass ?force=true to delete anyway.
     */
    @DeleteMapping("/api/config/credentials/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id, @RequestParam(defaultValue = "false") boolean force) {
        Credential credential = requireExists(id);

        List<String> referencingSchedules = scheduleRepository.findAll().stream()
                .filter(s -> s.actionConfig() != null && s.actionConfig().contains("${cred:" + credential.name() + "}"))
                .map(ScheduleDefinition::name)
                .toList();

        if (!referencingSchedules.isEmpty() && !force) {
            throw new ResourceInUseException(
                    "credential '" + credential.name() + "' is referenced by schedule(s): " +
                            String.join(", ", referencingSchedules) + " - pass force=true to delete anyway");
        }

        credentialRepository.deleteById(id);
    }

    private Credential requireExists(long id) {
        return credentialRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("credential " + id + " not found"));
    }

    private CredentialResponse toResponse(Credential credential) {
        return new CredentialResponse(
                credential.id(), credential.name(), Masking.lastFour(credential.value()),
                credential.description(), credential.updatedAt(), credential.updatedBy()
        );
    }
}
