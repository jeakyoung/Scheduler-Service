-- Schedule <-> target DB N:M (PLAN.md §4.3b, 2026-11-16 feature round) - replaces the single
-- schedule_definition.target_db_config_id FK with a join table so one schedule can target
-- multiple target_db_config rows at once (sequential execution, partial-failure tolerant -
-- see engine/execution/ScheduleExecutor, T17). Pre-production, so no data backfill is needed
-- (PLAN.md §4.1 2026-11-16 note) - the column is just dropped.

CREATE TABLE schedule_target_db_config (
    schedule_id         BIGINT NOT NULL,
    target_db_config_id BIGINT NOT NULL,
    CONSTRAINT PK_schedule_target_db_config PRIMARY KEY (schedule_id, target_db_config_id),
    CONSTRAINT FK_schedule_target_db_config_schedule
        FOREIGN KEY (schedule_id) REFERENCES schedule_definition (id) ON DELETE CASCADE,
    -- No ON DELETE clause on purpose: the default NO ACTION blocks deleting a target_db_config
    -- row that's still referenced by some schedule (PLAN.md §4.3b/§6 delete-block rule).
    CONSTRAINT FK_schedule_target_db_config_target_db
        FOREIGN KEY (target_db_config_id) REFERENCES target_db_config (id)
);

ALTER TABLE schedule_definition DROP CONSTRAINT FK_schedule_definition_target_db_config;
ALTER TABLE schedule_definition DROP COLUMN target_db_config_id;
