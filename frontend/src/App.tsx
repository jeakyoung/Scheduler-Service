import { useEffect } from "react";
import { Navigate, Route, Routes, useNavigate } from "react-router-dom";
import LoginPage from "./pages/LoginPage";
import ScheduleListPage from "./pages/ScheduleListPage";
import ScheduleFormPage from "./pages/ScheduleFormPage";
import LastResultPage from "./pages/LastResultPage";
import TargetDbConfigPage from "./pages/TargetDbConfigPage";
import CredentialPage from "./pages/CredentialPage";
import ChangePasswordPage from "./pages/ChangePasswordPage";
import ProtectedRoute from "./components/ProtectedRoute";
import Layout from "./components/Layout";
import { useAuth } from "./auth/AuthContext";

export default function App() {
  const { logout } = useAuth();
  const navigate = useNavigate();

  // Common 401 handling (TASKS.md T12): any API call that comes back unauthorized drops
  // the (in-memory only) token and bounces to /login, regardless of which page triggered it.
  useEffect(() => {
    function handleUnauthorized() {
      logout();
      navigate("/login");
    }
    window.addEventListener("auth:unauthorized", handleUnauthorized);
    return () => window.removeEventListener("auth:unauthorized", handleUnauthorized);
  }, [logout, navigate]);

  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<Layout />}>
          <Route path="/schedules" element={<ScheduleListPage />} />
          <Route path="/schedules/new" element={<ScheduleFormPage />} />
          <Route path="/schedules/:id/edit" element={<ScheduleFormPage />} />
          <Route path="/schedules/:id/last-result" element={<LastResultPage />} />
          <Route path="/target-db-configs" element={<TargetDbConfigPage />} />
          <Route path="/credentials" element={<CredentialPage />} />
          <Route path="/change-password" element={<ChangePasswordPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/schedules" replace />} />
    </Routes>
  );
}
