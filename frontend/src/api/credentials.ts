import { apiClient } from "./client";
import type { Credential, CredentialInput } from "./types";

export const credentialApi = {
  list: () => apiClient.get<Credential[]>("/api/config/credentials"),
  create: (input: CredentialInput) => apiClient.post<Credential>("/api/config/credentials", input),
  update: (id: number, input: CredentialInput) => apiClient.put<Credential>(`/api/config/credentials/${id}`, input),
  remove: (id: number, force = false) =>
    apiClient.delete<void>(`/api/config/credentials/${id}${force ? "?force=true" : ""}`),
};
