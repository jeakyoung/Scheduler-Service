import { apiClient } from "./client";
import type { ConnectionTestResult, TargetDbConfig, TargetDbConfigInput } from "./types";

export const targetDbConfigApi = {
  list: () => apiClient.get<TargetDbConfig[]>("/api/config/target-db"),
  create: (input: TargetDbConfigInput) => apiClient.post<TargetDbConfig>("/api/config/target-db", input),
  update: (id: number, input: TargetDbConfigInput) =>
    apiClient.put<TargetDbConfig>(`/api/config/target-db/${id}`, input),
  remove: (id: number) => apiClient.delete<void>(`/api/config/target-db/${id}`),
  // PLAN.md §6 (2026-11-18) - unsaved form input, nothing is persisted either side.
  testConnection: (input: TargetDbConfigInput) =>
    apiClient.post<ConnectionTestResult>("/api/config/target-db/test-connection", input),
  // Tests a saved config - the server decrypts, the client never sees/sends the plaintext password.
  testConnectionById: (id: number) =>
    apiClient.post<ConnectionTestResult>(`/api/config/target-db/${id}/test-connection`),
};
