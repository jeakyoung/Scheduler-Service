import { useEffect, useState, type FormEvent } from "react";
import { targetDbConfigApi } from "../api/targetDbConfigs";
import type { TargetDbConfig, TargetDbConfigInput } from "../api/types";

const EMPTY_FORM: TargetDbConfigInput = { name: "", jdbcUrl: "", username: "", password: "" };

/** PLAN.md §7 item 6 - 대상 DB 설정 목록/생성/수정/삭제, 비밀번호 마스킹. */
export default function TargetDbConfigPage() {
  const [configs, setConfigs] = useState<TargetDbConfig[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<TargetDbConfigInput>(EMPTY_FORM);
  const [showForm, setShowForm] = useState(false);

  async function load() {
    setLoading(true);
    try {
      setConfigs(await targetDbConfigApi.list());
    } catch {
      setError("목록을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  function startCreate() {
    setEditingId(null);
    setForm(EMPTY_FORM);
    setShowForm(true);
  }

  function startEdit(config: TargetDbConfig) {
    setEditingId(config.id);
    setForm({ name: config.name, jdbcUrl: config.jdbcUrl, username: config.username, password: "" });
    setShowForm(true);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      if (editingId !== null) {
        await targetDbConfigApi.update(editingId, form);
      } else {
        await targetDbConfigApi.create(form);
      }
      setShowForm(false);
      await load();
    } catch {
      setError("저장에 실패했습니다.");
    }
  }

  async function handleDelete(config: TargetDbConfig) {
    if (!window.confirm(`"${config.name}"을 삭제하시겠습니까?`)) return;
    try {
      await targetDbConfigApi.remove(config.id);
      await load();
    } catch {
      setError("삭제에 실패했습니다 (사용 중인 스케줄이 있을 수 있습니다).");
    }
  }

  return (
    <div>
      <div className="page-header">
        <h1>대상 DB 설정</h1>
        <button type="button" onClick={startCreate}>
          + 새 대상 DB
        </button>
      </div>
      {error && <p className="error-text">{error}</p>}

      {showForm && (
        <form onSubmit={handleSubmit} className="inline-form">
          <label>
            이름
            <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
          </label>
          <label>
            JDBC URL
            <input
              value={form.jdbcUrl}
              onChange={(e) => setForm({ ...form, jdbcUrl: e.target.value })}
              placeholder="jdbc:sqlserver://example-business-db;databaseName=erp;"
              required
            />
          </label>
          <label>
            사용자명
            <input value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} required />
          </label>
          <label>
            비밀번호 {editingId !== null && "(수정 시에도 다시 입력해야 합니다 - 백엔드는 PUT을 전체 교체로 처리합니다)"}
            <input type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} required />
          </label>
          <div className="form-actions">
            <button type="submit">저장</button>
            <button type="button" onClick={() => setShowForm(false)}>
              취소
            </button>
          </div>
        </form>
      )}

      {loading ? (
        <p>불러오는 중...</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>이름</th>
              <th>JDBC URL</th>
              <th>사용자명</th>
              <th>비밀번호</th>
              <th>작업</th>
            </tr>
          </thead>
          <tbody>
            {configs.map((c) => (
              <tr key={c.id}>
                <td>{c.name}</td>
                <td>{c.jdbcUrl}</td>
                <td>{c.username}</td>
                <td>
                  <code>{c.maskedPassword}</code>
                </td>
                <td className="actions">
                  <button type="button" onClick={() => startEdit(c)}>
                    수정
                  </button>
                  <button type="button" onClick={() => handleDelete(c)}>
                    삭제
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
