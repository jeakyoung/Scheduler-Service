import { apiClient } from "./client";
import type { LoginResponse, MeResponse } from "./types";

export const authApi = {
  login: (username: string, password: string) =>
    apiClient.post<LoginResponse>("/api/auth/login", { username, password }),
  logout: () => apiClient.post<void>("/api/auth/logout"),
  me: () => apiClient.get<MeResponse>("/api/auth/me"),
  changePassword: (currentPassword: string, newPassword: string) =>
    apiClient.put<void>("/api/auth/password", { currentPassword, newPassword }),
};
