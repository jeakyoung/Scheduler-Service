import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";

// AuthContext.isAuthDisabled is computed once at module-load time from
// import.meta.env.VITE_AUTH_DISABLED, so each case below stubs the env var and then
// dynamically re-imports the module fresh (vi.resetModules clears the registry) rather than
// relying on a static top-level import - a static import would freeze the flag's value at
// whatever it happened to be when this test file first loaded.
async function renderProtectedRouteTree() {
  const { AuthProvider } = await import("./AuthContext");
  const { default: ProtectedRoute } = await import("../components/ProtectedRoute");

  render(
    <MemoryRouter initialEntries={["/schedules"]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<div>LOGIN_SCREEN</div>} />
          <Route element={<ProtectedRoute />}>
            <Route path="/schedules" element={<div>PROTECTED_CONTENT</div>} />
          </Route>
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe("VITE_AUTH_DISABLED frontend bypass", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.resetModules();
  });

  it("renders protected content without redirecting to /login when VITE_AUTH_DISABLED=true", async () => {
    vi.stubEnv("VITE_AUTH_DISABLED", "true");
    vi.resetModules();

    await renderProtectedRouteTree();

    expect(screen.getByText("PROTECTED_CONTENT")).toBeInTheDocument();
    expect(screen.queryByText("LOGIN_SCREEN")).not.toBeInTheDocument();
  });

  it("still redirects to /login when VITE_AUTH_DISABLED is unset (default, unchanged behavior)", async () => {
    vi.stubEnv("VITE_AUTH_DISABLED", "");
    vi.resetModules();

    await renderProtectedRouteTree();

    expect(screen.getByText("LOGIN_SCREEN")).toBeInTheDocument();
    expect(screen.queryByText("PROTECTED_CONTENT")).not.toBeInTheDocument();
  });
});
