// In-memory only, deliberately not persisted to localStorage (auth decided 2026-11-13:
// stateless JWT via Authorization header, not a cookie). This means a full page reload
// logs the user out - acceptable for a single-admin-role internal tool, and avoids the XSS
// exposure of a long-lived token sitting in localStorage.
let currentToken: string | null = null;

export function setAuthToken(token: string | null): void {
  currentToken = token;
}

export function getAuthToken(): string | null {
  return currentToken;
}
