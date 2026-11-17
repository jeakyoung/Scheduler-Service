import { useEffect, useState, type FormEvent } from "react";
import { credentialApi } from "../api/credentials";
import type { Credential, CredentialInput } from "../api/types";
import { ApiError } from "../api/client";

const EMPTY_FORM: CredentialInput = { name: "", value: "", description: "" };

/** PLAN.md §7 item 7 - 자격증명 목록/생성/수정/삭제, 값 마스킹. */
export default function CredentialPage() {
  const [credentials, setCredentials] = useState<Credential[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<CredentialInput>(EMPTY_FORM);
  const [showForm, setShowForm] = useState(false);

  async function load() {
    setLoading(true);
    try {
      setCredentials(await credentialApi.list());
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

  function startEdit(credential: Credential) {
    setEditingId(credential.id);
    setForm({ name: credential.name, value: "", description: credential.description ?? "" });
    setShowForm(true);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      if (editingId !== null) {
        await credentialApi.update(editingId, form);
      } else {
        await credentialApi.create(form);
      }
      setShowForm(false);
      await load();
    } catch {
      setError("저장에 실패했습니다.");
    }
  }

  async function handleDelete(credential: Credential) {
    if (!window.confirm(`"${credential.name}"을 삭제하시겠습니까?`)) return;
    try {
      await credentialApi.remove(credential.id);
      await load();
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        const forceDelete = window.confirm(
          `다른 스케줄이 이 자격증명을 참조하고 있습니다.\n${err.message}\n\n그래도 삭제하시겠습니까?`
        );
        if (forceDelete) {
          await credentialApi.remove(credential.id, true);
          await load();
        }
      } else {
        setError("삭제에 실패했습니다.");
      }
    }
  }

  return (
    <div>
      <div className="page-header">
        <h1>자격증명</h1>
        <button type="button" className="btn-primary" onClick={startCreate}>
          + 새 자격증명
        </button>
      </div>
      {error && <p className="error-text">{error}</p>}

      {showForm && (
        <form onSubmit={handleSubmit} className="inline-form">
          <label>
            이름 (action_config에서 <code>{"${cred:NAME}"}</code>로 참조)
            <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
          </label>
          <label>
            값 {editingId !== null && "(수정 시에도 다시 입력해야 합니다 - PUT은 전체 교체)"}
            <input type="password" value={form.value} onChange={(e) => setForm({ ...form, value: e.target.value })} required />
          </label>
          <label>
            설명
            <input value={form.description ?? ""} onChange={(e) => setForm({ ...form, description: e.target.value })} />
          </label>
          <div className="form-actions">
            <button type="submit" className="btn-primary">
              저장
            </button>
            <button type="button" onClick={() => setShowForm(false)}>
              취소
            </button>
          </div>
        </form>
      )}

      {loading ? (
        <p className="loading-text">
          <span className="spinner" aria-hidden="true" /> 불러오는 중...
        </p>
      ) : credentials.length === 0 ? (
        <p className="empty-state">등록된 자격증명이 없습니다. "+ 새 자격증명"으로 시작하세요.</p>
      ) : (
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>이름</th>
                <th>값</th>
                <th>설명</th>
                <th>작업</th>
              </tr>
            </thead>
            <tbody>
              {credentials.map((c) => (
                <tr key={c.id}>
                  <td>{c.name}</td>
                  <td>
                    <code>{c.maskedValue}</code>
                  </td>
                  <td>{c.description}</td>
                  <td className="actions">
                    <button type="button" onClick={() => startEdit(c)}>
                      수정
                    </button>
                    <button type="button" className="btn-danger" onClick={() => handleDelete(c)}>
                      삭제
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
