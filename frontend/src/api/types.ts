export type ActionType = "API_CALL" | "DISPLAY_ONLY";
export type GroupingMode = "PER_ROW" | "GROUP_BY";
export type ParamDataType = "STRING" | "INT" | "DECIMAL" | "DATE" | "DATETIME" | "BOOLEAN";
export type ValueSource = "LITERAL" | "CURRENT_DATE" | "CURRENT_DATETIME";
export type ExecutionStatus = "SUCCESS" | "PARTIAL_SUCCESS" | "FAILURE";
export type TriggerType = "SCHEDULED" | "MANUAL";

export interface ApiCallActionConfig {
  method: string;
  url: string;
  headers: Record<string, string>;
  groupingMode: GroupingMode;
  groupByField: string | null;
  bodyTemplate: string;
}

export interface DisplayOnlyActionConfig {
  displayFormat: string;
  columns: string[] | null;
}

export interface ScheduleParameter {
  id: number;
  paramOrder: number;
  paramName: string;
  dataType: ParamDataType;
  valueSource: ValueSource;
  literalValue: string | null;
}

export interface ScheduleParameterInput {
  paramOrder: number;
  paramName: string;
  dataType: ParamDataType;
  valueSource: ValueSource;
  literalValue: string | null;
}

export interface TargetDbConfigRef {
  id: number;
  name: string;
}

export interface Schedule {
  id: number;
  name: string;
  enabled: boolean;
  cronExpression: string;
  timezone: string;
  spName: string;
  targetDbConfigIds: number[];
  targetDbConfigs: TargetDbConfigRef[];
  actionType: ActionType;
  actionConfig: ApiCallActionConfig | DisplayOnlyActionConfig;
  description: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  updatedBy: string | null;
  nextFireTime: string | null;
  parameters: ScheduleParameter[];
}

export interface ScheduleInput {
  name: string;
  enabled: boolean;
  cronExpression: string;
  timezone: string;
  spName: string;
  targetDbConfigIds: number[];
  actionType: ActionType;
  actionConfig: ApiCallActionConfig | DisplayOnlyActionConfig;
  description?: string | null;
}

// 2026-11-16 (multi-target-DB round, PLAN.md §3.1b): one schedule run now visits N target DBs,
// so the overall ExecutionResult carries a per-target-DB breakdown. targetDbResults[i].status
// is always SUCCESS/FAILURE, never PARTIAL_SUCCESS (that's only meaningful once combined).
export interface TargetDbExecutionResult {
  targetDbConfigId: number;
  targetDbConfigName: string;
  status: ExecutionStatus;
  summary: string;
  errorMessage: string | null;
}

export interface ExecutionResult {
  scheduleId: number;
  triggerType: TriggerType;
  startedAt: string;
  finishedAt: string;
  status: ExecutionStatus;
  summary: string;
  errorMessage: string | null;
  targetDbResults: TargetDbExecutionResult[];
}

// One DisplayOutput now always belongs to exactly one target DB - never merged across target
// DBs (PLAN.md §3.3).
export interface DisplayOutput {
  scheduleId: number;
  targetDbConfigId: number;
  targetDbConfigName: string;
  columns: string[];
  rows: Record<string, unknown>[];
  generatedAt: string;
}

export interface RunResult {
  result: ExecutionResult;
  displayOutputs: DisplayOutput[];
}

export interface ScheduleStatus {
  scheduleId: number;
  nextFireTime: string | null;
  running: boolean;
}

export interface TargetDbConfig {
  id: number;
  name: string;
  jdbcUrl: string;
  username: string;
  maskedPassword: string;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface TargetDbConfigInput {
  name: string;
  jdbcUrl: string;
  username: string;
  password: string;
}

export interface Credential {
  id: number;
  name: string;
  maskedValue: string;
  description: string | null;
  updatedAt: string | null;
  updatedBy: string | null;
}

export interface CredentialInput {
  name: string;
  value: string;
  description?: string | null;
}

export interface LoginResponse {
  token: string;
  username: string;
  role: string;
}

export interface MeResponse {
  username: string;
  role: string;
}
