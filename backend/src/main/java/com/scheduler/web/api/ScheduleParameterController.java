package com.scheduler.web.api;

import com.scheduler.domain.schedule.ScheduleParameter;
import com.scheduler.domain.schedule.ScheduleParameterRepository;
import com.scheduler.domain.schedule.ScheduleRepository;
import com.scheduler.domain.schedule.ValueSource;
import com.scheduler.engine.execution.ScheduleNotFoundException;
import com.scheduler.engine.sp.SpParameterResolver;
import com.scheduler.web.api.dto.ScheduleParameterRequest;
import com.scheduler.web.api.dto.ScheduleParameterResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** PLAN.md §6: "파라미터 목록 전체 교체(단순화를 위해 개별 CRUD 대신 통째 교체)". */
@RestController
public class ScheduleParameterController {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleParameterRepository scheduleParameterRepository;

    public ScheduleParameterController(ScheduleRepository scheduleRepository, ScheduleParameterRepository scheduleParameterRepository) {
        this.scheduleRepository = scheduleRepository;
        this.scheduleParameterRepository = scheduleParameterRepository;
    }

    @PutMapping("/api/schedules/{id}/parameters")
    public List<ScheduleParameterResponse> replaceAll(@PathVariable long id, @Valid @RequestBody List<ScheduleParameterRequest> requests) {
        scheduleRepository.findById(id).orElseThrow(() -> new ScheduleNotFoundException(id));

        List<ScheduleParameter> toSave = requests.stream().map(r -> toDomain(id, r)).toList();
        validateLiterals(toSave);

        return scheduleParameterRepository.replaceAll(id, toSave).stream()
                .map(p -> new ScheduleParameterResponse(p.id(), p.paramOrder(), p.paramName(), p.dataType(), p.valueSource(), p.literalValue()))
                .toList();
    }

    private ScheduleParameter toDomain(long scheduleId, ScheduleParameterRequest request) {
        if (request.valueSource() == ValueSource.LITERAL
                && (request.literalValue() == null || request.literalValue().isBlank())) {
            throw new IllegalArgumentException("literalValue is required when valueSource is LITERAL (param: " + request.paramName() + ")");
        }
        return new ScheduleParameter(null, scheduleId, request.paramOrder(), request.paramName(),
                request.dataType(), request.valueSource(), request.literalValue());
    }

    private void validateLiterals(List<ScheduleParameter> parameters) {
        try {
            SpParameterResolver.resolve(parameters);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid parameter literal value: " + e.getMessage());
        }
    }
}
