package com.scheduler.infra.persistence.jdbc;

import com.scheduler.domain.config.TargetDbConfig;
import com.scheduler.domain.schedule.ActionConfigJson;
import com.scheduler.domain.schedule.ActionType;
import com.scheduler.domain.schedule.DisplayOnlyActionConfig;
import com.scheduler.domain.schedule.ScheduleDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T16 (PLAN.md §4.3b): the schedule_target_db_config join table repository. */
class JdbcScheduleTargetDbConfigRepositoryTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private JdbcScheduleTargetDbConfigRepository repository;

    @Autowired
    private JdbcScheduleRepository scheduleRepository;

    @Autowired
    private JdbcTargetDbConfigRepository targetDbConfigRepository;

    private long scheduleId;
    private long targetDbConfigA;
    private long targetDbConfigB;
    private long targetDbConfigC;

    @BeforeEach
    void seed() {
        ScheduleDefinition schedule = scheduleRepository.save(new ScheduleDefinition(
                null, "N:M test schedule", false, "0 0 9 ? * MON", "Asia/Seoul",
                "SP_SAMPLE", ActionType.DISPLAY_ONLY,
                ActionConfigJson.write(new DisplayOnlyActionConfig(null, null)),
                null, null, null, "tester"
        ));
        scheduleId = schedule.id();

        targetDbConfigA = targetDbConfigRepository.save(new TargetDbConfig(
                null, "A", "jdbc:sqlserver://a;", "u", "pw", null, null)).id();
        targetDbConfigB = targetDbConfigRepository.save(new TargetDbConfig(
                null, "B", "jdbc:sqlserver://b;", "u", "pw", null, null)).id();
        targetDbConfigC = targetDbConfigRepository.save(new TargetDbConfig(
                null, "C", "jdbc:sqlserver://c;", "u", "pw", null, null)).id();
    }

    @Test
    void scheduleWithNoLinksHasEmptyList() {
        assertThat(repository.findTargetDbConfigIdsByScheduleId(scheduleId)).isEmpty();
    }

    @Test
    void replaceForScheduleOverwritesPreviousLinksAddingAndRemoving() {
        repository.replaceForSchedule(scheduleId, List.of(targetDbConfigA, targetDbConfigB));
        assertThat(repository.findTargetDbConfigIdsByScheduleId(scheduleId))
                .containsExactlyInAnyOrder(targetDbConfigA, targetDbConfigB);

        // Second call must fully replace, not merge: A is dropped, C is newly added, B stays.
        repository.replaceForSchedule(scheduleId, List.of(targetDbConfigB, targetDbConfigC));
        assertThat(repository.findTargetDbConfigIdsByScheduleId(scheduleId))
                .containsExactlyInAnyOrder(targetDbConfigB, targetDbConfigC);
    }

    @Test
    void replaceForScheduleWithEmptyListClearsAllLinks() {
        repository.replaceForSchedule(scheduleId, List.of(targetDbConfigA, targetDbConfigB));

        repository.replaceForSchedule(scheduleId, List.of());

        assertThat(repository.findTargetDbConfigIdsByScheduleId(scheduleId)).isEmpty();
    }

    @Test
    void deletingScheduleCascadesTargetDbConfigLinks() {
        repository.replaceForSchedule(scheduleId, List.of(targetDbConfigA, targetDbConfigB));

        scheduleRepository.deleteById(scheduleId);

        assertThat(repository.findTargetDbConfigIdsByScheduleId(scheduleId)).isEmpty();
    }

    @Test
    void replaceForScheduleWithNonExistentTargetDbConfigIdFailsWithForeignKeyViolation() {
        long nonExistentId = targetDbConfigC + 999_999L;

        assertThatThrownBy(() -> repository.replaceForSchedule(scheduleId, List.of(nonExistentId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findScheduleIdsByTargetDbConfigIdReturnsReferencingSchedules() {
        repository.replaceForSchedule(scheduleId, List.of(targetDbConfigA));

        assertThat(repository.findScheduleIdsByTargetDbConfigId(targetDbConfigA)).containsExactly(scheduleId);
        assertThat(repository.findScheduleIdsByTargetDbConfigId(targetDbConfigB)).isEmpty();
    }
}
