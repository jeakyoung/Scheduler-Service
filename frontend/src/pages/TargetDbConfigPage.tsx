import { useEffect, useState, type FormEvent } from "react";
import { targetDbConfigApi } from "../api/targetDbConfigs";
import type { TargetDbConfig, TargetDbConfigInput } from "../api/types";

const EMPTY_FORM: TargetDbConfigInput = { name: "", jdbcUrl: "", username: "", password: "" };

// Form-level test result (unsaved input) - "idle" means nothing tested yet this form session.
type FormTestResult = { status: "idle" | "testing" | "success" | "failure"; message?: string };

// Per-row test result (saved config, tested via its id). A row with no entry here renders as
// "미확인" (unknown) - this map is plain React state, never persisted, so a page refresh wipes
// it and every row starts over at "미확인" (PLAN.md §7 item 6 - deliberately not cached).
type RowTestResult = { status: "testing" | "success" | "failure"; message?: string };

/** PLAN.md §7 item 6 - 대상 DB 설정 목록/생성/수정/삭제, 비밀번호 마스킹. */
export default function TargetDbConfigPage() {
  const [configs, setConfigs] = useState<TargetDbConfig[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<TargetDbConfigInput>(EMPTY_FORM);
  const [showForm, setShowForm] = useState(false);
  const [formTestResult, setFormTestResult] = useState<FormTestResult>({ status: "idle" });
  const [rowTestResults, setRowTestResults] = useState<Record<number, RowTestResult>>({});

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
    setFormTestResult({ status: "idle" });
    setShowForm(true);
  }

  function startEdit(config: TargetDbConfig) {
    setEditingId(config.id);
    setForm({ name: config.name, jdbcUrl: config.jdbcUrl, username: config.username, password: "" });
    setFormTestResult({ status: "idle" });
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

  // Tests the form's current (unsaved) input - PLAN.md §6/§7: nothing is persisted either side.
  async function handleTestConnection() {
    setFormTestResult({ status: "testing" });
    try {
      const result = await targetDbConfigApi.testConnection(form);
      setFormTestResult({ status: result.success ? "success" : "failure", message: result.message });
    } catch {
      setFormTestResult({ status: "failure", message: "연결 테스트 요청에 실패했습니다." });
    }
  }

  // Tests a saved config by id - only this row's badge updates, others are untouched.
  async function handleTestConnectionById(id: number) {
    setRowTestResults((prev) => ({ ...prev, [id]: { status: "testing" } }));
    try {
      const result = await targetDbConfigApi.testConnectionById(id);
      setRowTestResults((prev) => ({
        ...prev,
        [id]: { status: result.success ? "success" : "failure", message: result.message },
      }));
    } catch {
      setRowTestResults((prev) => ({
        ...prev,
        [id]: { status: "failure", message: "연결 테스트 요청에 실패했습니다." },
      }));
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
        <button type="button" className="btn-primary" onClick={startCreate}>
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
            <button type="submit" className="btn-primary">
              저장
            </button>
            <button type="button" onClick={handleTestConnection} disabled={formTestResult.status === "testing"}>
              연결 테스트
            </button>
            <button type="button" onClick={() => setShowForm(false)}>
              취소
            </button>
          </div>
          {formTestResult.status === "testing" && (
            <p className="loading-text">
              <span className="spinner" aria-hidden="true" /> 연결 테스트 중...
            </p>
          )}
          {formTestResult.status === "success" && <p className="status-success">연결 성공</p>}
          {formTestResult.status === "failure" && (
            <p className="status-failure">연결 실패: {formTestResult.message}</p>
          )}
        </form>
      )}

      {loading ? (
        <p className="loading-text">
          <span className="spinner" aria-hidden="true" /> 불러오는 중...
        </p>
      ) : configs.length === 0 ? (
        <p className="empty-state">등록된 대상 DB가 없습니다. "+ 새 대상 DB"로 시작하세요.</p>
      ) : (
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>이름</th>
                <th>JDBC URL</th>
                <th>사용자명</th>
                <th>비밀번호</th>
                <th>연결 상태</th>
                <th>작업</th>
              </tr>
            </thead>
            <tbody>
              {configs.map((c) => {
                const rowResult = rowTestResults[c.id];
                return (
                  <tr key={c.id}>
                    <td>{c.name}</td>
                    <td>{c.jdbcUrl}</td>
                    <td>{c.username}</td>
                    <td>
                      <code>{c.maskedPassword}</code>
                    </td>
                    <td className="connection-status">
                      {!rowResult && <span className="status-unknown">미확인</span>}
                      {rowResult?.status === "testing" && (
                        <span className="loading-text">
                          <span className="spinner" aria-hidden="true" /> 테스트 중...
                        </span>
                      )}
                      {rowResult?.status === "success" && <span className="status-success">연결 성공</span>}
                      {rowResult?.status === "failure" && (
                        <span className="status-failure" title={rowResult.message}>
                          연결 실패{rowResult.message ? `: ${rowResult.message}` : ""}
                        </span>
                      )}
                      <button
                        type="button"
                        onClick={() => handleTestConnectionById(c.id)}
                        disabled={rowResult?.status === "testing"}
                      >
                        테스트
                      </button>
                    </td>
                    <td className="actions">
                      <button type="button" onClick={() => startEdit(c)}>
                        수정
                      </button>
                      <button type="button" className="btn-danger" onClick={() => handleDelete(c)}>
                        삭제
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
