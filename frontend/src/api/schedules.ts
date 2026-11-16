import { apiClient } from "./client";
import type {
  RunResult,
  Schedule,
  ScheduleInput,
  ScheduleParameter,
  ScheduleParameterInput,
  ScheduleStatus,
} from "./types";

export const scheduleApi = {
  list: () => apiClient.get<Schedule[]>("/api/schedules"),
  get: (id: number) => apiClient.get<Schedule>(`/api/schedules/${id}`),
  create: (input: ScheduleInput) => apiClient.post<Schedule>("/api/schedules", input),
  update: (id: number, input: ScheduleInput) => apiClient.put<Schedule>(`/api/schedules/${id}`, input),
  remove: (id: number) => apiClient.delete<void>(`/api/schedules/${id}`),
  setEnabled: (id: number, enabled: boolean) =>
    apiClient.patch<Schedule>(`/api/schedules/${id}/enabled`, { enabled }),
  replaceParameters: (id: number, parameters: ScheduleParameterInput[]) =>
    apiClient.put<ScheduleParameter[]>(`/api/schedules/${id}/parameters`, parameters),
  runNow: (id: number) => apiClient.post<RunResult>(`/api/schedules/${id}/run-now`),
  lastResult: (id: number) => apiClient.get<RunResult | undefined>(`/api/schedules/${id}/last-result`),
  status: (id: number) => apiClient.get<ScheduleStatus>(`/api/schedules/${id}/status`),
};
