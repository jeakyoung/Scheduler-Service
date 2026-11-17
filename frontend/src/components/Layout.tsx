import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { isAuthDisabled, useAuth } from "../auth/AuthContext";

export default function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate("/login");
  }

  return (
    <div className="app-layout">
      <nav className="app-nav">
        {isAuthDisabled && <span className="auth-disabled-badge">로컬 인증 비활성화</span>}
        <NavLink to="/schedules">스케줄</NavLink>
        <NavLink to="/target-db-configs">대상 DB 설정</NavLink>
        <NavLink to="/credentials">자격증명</NavLink>
        <NavLink to="/change-password">비밀번호 변경</NavLink>
        <span className="app-nav-spacer" />
        <span className="app-nav-user">{user?.username}</span>
        <button type="button" onClick={handleLogout}>
          로그아웃
        </button>
      </nav>
      <main className="app-main">
        <Outlet />
      </main>
    </div>
  );
}
