import { Navigate, Route, Routes } from "react-router-dom";
import LoginPage from "./pages/LoginPage";
import ScheduleListPage from "./pages/ScheduleListPage";
import ScheduleFormPage from "./pages/ScheduleFormPage";
import RunResultPage from "./pages/RunResultPage";
import LastResultPage from "./pages/LastResultPage";
import TargetDbConfigPage from "./pages/TargetDbConfigPage";
import CredentialPage from "./pages/CredentialPage";
import ChangePasswordPage from "./pages/ChangePasswordPage";

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/schedules" element={<ScheduleListPage />} />
      <Route path="/schedules/new" element={<ScheduleFormPage />} />
      <Route path="/schedules/:id/edit" element={<ScheduleFormPage />} />
      <Route path="/schedules/:id/run-result" element={<RunResultPage />} />
      <Route path="/schedules/:id/last-result" element={<LastResultPage />} />
      <Route path="/target-db-configs" element={<TargetDbConfigPage />} />
      <Route path="/credentials" element={<CredentialPage />} />
      <Route path="/change-password" element={<ChangePasswordPage />} />
      <Route path="*" element={<Navigate to="/schedules" replace />} />
    </Routes>
  );
}
