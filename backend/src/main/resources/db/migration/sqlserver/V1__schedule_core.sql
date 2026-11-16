-- Meta DB core schema: schedule definitions/parameters + downstream config + admin accounts.
-- Execution-history table is intentionally NOT created here (PLAN.md §3/§4/§9 - TBD, future round).
-- Column shapes follow PLAN.md §4.1~4.5 exactly.

-- Target business DB is fixed to MSSQL (PLAN.md §1/§4.3) - no db_type/DBMS-selection column.
CREATE TABLE target_db_config (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    name                NVARCHAR(100)   NOT NULL,
    jdbc_url            NVARCHAR(500)   NOT NULL,
    username            NVARCHAR(200)   NOT NULL,
    encrypted_password  NVARCHAR(MAX)   NOT NULL,
    created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME()
);

CREATE TABLE credential (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    name                NVARCHAR(100)   NOT NULL,
    encrypted_value     NVARCHAR(MAX)   NOT NULL,
    description         NVARCHAR(300)   NULL,
    updated_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by          NVARCHAR(100)   NULL,
    CONSTRAINT UQ_credential_name UNIQUE (name)
);

CREATE TABLE admin_user (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    username            NVARCHAR(100)   NOT NULL,
    password_hash       NVARCHAR(200)   NOT NULL,
    role                NVARCHAR(20)    NOT NULL DEFAULT 'ADMIN',
    created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT UQ_admin_user_username UNIQUE (username)
);

CREATE TABLE schedule_definition (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    name                NVARCHAR(200)   NOT NULL,
    enabled             BIT             NOT NULL DEFAULT 0,
    cron_expression     NVARCHAR(100)   NOT NULL,
    timezone            NVARCHAR(50)    NOT NULL DEFAULT 'Asia/Seoul',
    sp_name             NVARCHAR(200)   NOT NULL,
    target_db_config_id BIGINT          NOT NULL,
    action_type         NVARCHAR(20)    NOT NULL,
    action_config       NVARCHAR(MAX)   NOT NULL,
    description         NVARCHAR(500)   NULL,
    created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by          NVARCHAR(100)   NULL,
    CONSTRAINT FK_schedule_definition_target_db_config
        FOREIGN KEY (target_db_config_id) REFERENCES target_db_config (id)
);

CREATE TABLE schedule_parameter (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    schedule_id         BIGINT          NOT NULL,
    param_order         INT             NOT NULL,
    param_name          NVARCHAR(100)   NOT NULL,
    data_type           NVARCHAR(20)    NOT NULL,
    value_source        NVARCHAR(20)    NOT NULL,
    literal_value       NVARCHAR(500)   NULL,
    CONSTRAINT FK_schedule_parameter_schedule_definition
        FOREIGN KEY (schedule_id) REFERENCES schedule_definition (id) ON DELETE CASCADE
);

CREATE INDEX IX_schedule_parameter_schedule_id ON schedule_parameter (schedule_id);
