import { createContext, useCallback, useContext, useState, type ReactNode } from "react";
import { authApi } from "../api/auth";
import { setAuthToken } from "../api/authToken";
import type { MeResponse } from "../api/types";

interface AuthContextValue {
  user: MeResponse | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

// LOCAL DEV ONLY - NEVER set this in production. Mirrors the backend's AUTH_DISABLED: the
// backend flag skips server-side token checks, but ProtectedRoute's client-side redirect to
// /login is a separate gate that only cares about AuthContext.user being non-null - that's
// why a backend-only AUTH_DISABLED still leaves the SPA stuck on the login screen. Must be
// turned on together with the backend flag to actually have any effect (see README.md).
export const isAuthDisabled = import.meta.env.VITE_AUTH_DISABLED?.toLowerCase() === "true";

const LOCAL_DEV_DUMMY_USER: MeResponse = { username: "local-dev", role: "ADMIN" };

export function AuthProvider({ children }: { children: ReactNode }) {
  // No real login call and no setAuthToken(...) in this branch on purpose - the backend
  // isn't checking tokens either when its own AUTH_DISABLED is on, so the API client simply
  // omits the Authorization header (its existing no-token behavior) and every request still
  // goes through.
  const [user, setUser] = useState<MeResponse | null>(isAuthDisabled ? LOCAL_DEV_DUMMY_USER : null);

  const login = useCallback(async (username: string, password: string) => {
    const response = await authApi.login(username, password);
    setAuthToken(response.token);
    setUser({ username: response.username, role: response.role });
  }, []);

  const logout = useCallback(() => {
    setAuthToken(null);
    setUser(null);
  }, []);

  return <AuthContext.Provider value={{ user, login, logout }}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
