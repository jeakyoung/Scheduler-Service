import { apiClient } from "./client";
import type { TargetDbConfig, TargetDbConfigInput } from "./types";

export const targetDbConfigApi = {
  list: () => apiClient.get<TargetDbConfig[]>("/api/config/target-db"),
  create: (input: TargetDbConfigInput) => apiClient.post<TargetDbConfig>("/api/config/target-db", input),
  update: (id: number, input: TargetDbConfigInput) =>
    apiClient.put<TargetDbConfig>(`/api/config/target-db/${id}`, input),
  remove: (id: number) => apiClient.delete<void>(`/api/config/target-db/${id}`),
};
