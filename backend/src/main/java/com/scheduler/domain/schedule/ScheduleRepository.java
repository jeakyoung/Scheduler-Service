package com.scheduler.domain.schedule;

import java.util.List;
import java.util.Optional;

public interface ScheduleRepository {

    List<ScheduleDefinition> findAll();

    List<ScheduleDefinition> findAllEnabled();

    Optional<ScheduleDefinition> findById(long id);

    ScheduleDefinition save(ScheduleDefinition schedule);

    ScheduleDefinition update(ScheduleDefinition schedule);

    void deleteById(long id);

    void updateEnabled(long id, boolean enabled, String updatedBy);
}
