-- H2 equivalent of ../sqlserver/V3__schedule_target_db_multi.sql (PLAN.md §4.3b) - identical
-- shape, H2's ALTER TABLE DROP CONSTRAINT/DROP COLUMN syntax matches SQL Server's here so the
-- script is the same besides the file location (vendor split exists for V1/V2's DDL dialect
-- differences, not because this particular change needs H2-specific syntax).

CREATE TABLE schedule_target_db_config (
    schedule_id         BIGINT NOT NULL,
    target_db_config_id BIGINT NOT NULL,
    CONSTRAINT PK_schedule_target_db_config PRIMARY KEY (schedule_id, target_db_config_id),
    CONSTRAINT FK_schedule_target_db_config_schedule
        FOREIGN KEY (schedule_id) REFERENCES schedule_definition (id) ON DELETE CASCADE,
    CONSTRAINT FK_schedule_target_db_config_target_db
        FOREIGN KEY (target_db_config_id) REFERENCES target_db_config (id)
);

ALTER TABLE schedule_definition DROP CONSTRAINT FK_schedule_definition_target_db_config;
ALTER TABLE schedule_definition DROP COLUMN target_db_config_id;
