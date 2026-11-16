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

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<MeResponse | null>(null);

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
