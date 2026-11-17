import { useState, type FormEvent } from "react";
import { authApi } from "../api/auth";
import { ApiError } from "../api/client";

/** PLAN.md §7 item 8 - 본인 계정 비밀번호 변경, 최소 수준의 계정 관리. */
export default function ChangePasswordPage() {
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSuccess(false);

    if (newPassword !== confirmPassword) {
      setError("새 비밀번호가 일치하지 않습니다.");
      return;
    }
    if (newPassword.length < 8) {
      setError("새 비밀번호는 8자 이상이어야 합니다.");
      return;
    }

    setSubmitting(true);
    try {
      await authApi.changePassword(currentPassword, newPassword);
      setSuccess(true);
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
    } catch (err) {
      setError(err instanceof ApiError && err.status === 401 ? "현재 비밀번호가 올바르지 않습니다." : "비밀번호 변경에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div>
      <h1>비밀번호 변경</h1>
      <form onSubmit={handleSubmit} className="inline-form">
        <label>
          현재 비밀번호
          <input type="password" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} required />
        </label>
        <label>
          새 비밀번호 (8자 이상)
          <input type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} required minLength={8} />
        </label>
        <label>
          새 비밀번호 확인
          <input type="password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} required />
        </label>
        {error && <p className="error-text">{error}</p>}
        {success && <p className="status-success">비밀번호가 변경되었습니다.</p>}
        <button type="submit" className="btn-primary" disabled={submitting}>
          {submitting ? "변경 중..." : "변경"}
        </button>
      </form>
    </div>
  );
}
