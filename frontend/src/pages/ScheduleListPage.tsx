import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { scheduleApi } from "../api/schedules";
import type { RunResult, Schedule } from "../api/types";
import RunResultModal from "../components/RunResultModal";
import { ApiError } from "../api/client";
import { statusClassName, statusLabel } from "../components/statusBadge";

/** PLAN.md §7 item 2 - enabled 토글, cron, 다음 실행 예정시각, 마지막 실행 상태(인메모리 캐시 기반), "지금 실행". */
export default function ScheduleListPage() {
  const [schedules, setSchedules] = useState<Schedule[]>([]);
  const [lastResults, setLastResults] = useState<Record<number, RunResult | undefined>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [runningId, setRunningId] = useState<number | null>(null);
  const [activeRunResult, setActiveRunResult] = useState<{ schedule: Schedule; result: RunResult } | null>(null);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const list = await scheduleApi.list();
      setSchedules(list);

      const results = await Promise.all(
        list.map(async (s) => {
          try {
            return [s.id, await scheduleApi.lastResult(s.id)] as const;
          } catch {
            return [s.id, undefined] as const;
          }
        })
      );
      setLastResults(Object.fromEntries(results));
    } catch {
      setError("스케줄 목록을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  async function handleToggleEnabled(schedule: Schedule) {
    try {
      await scheduleApi.setEnabled(schedule.id, !schedule.enabled);
      await load();
    } catch {
      setError("활성/비활성 전환에 실패했습니다.");
    }
  }

  async function handleDelete(schedule: Schedule) {
    if (!window.confirm(`"${schedule.name}" 스케줄을 삭제하시겠습니까?`)) return;
    try {
      await scheduleApi.remove(schedule.id);
      await load();
    } catch {
      setError("삭제에 실패했습니다.");
    }
  }

  async function handleRunNow(schedule: Schedule) {
    setRunningId(schedule.id);
    setError(null);
    try {
      const result = await scheduleApi.runNow(schedule.id);
      setActiveRunResult({ schedule, result });
      await load();
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setError(`"${schedule.name}"은 이미 실행 중입니다.`);
      } else {
        setError("즉시 실행에 실패했습니다.");
      }
    } finally {
      setRunningId(null);
    }
  }

  if (loading) return <p>불러오는 중...</p>;

  return (
    <div>
      <div className="page-header">
        <h1>스케줄 목록</h1>
        <Link to="/schedules/new">
          <button type="button">+ 새 스케줄</button>
        </Link>
      </div>
      {error && <p className="error-text">{error}</p>}

      <table>
        <thead>
          <tr>
            <th>이름</th>
            <th>활성</th>
            <th>Cron</th>
            <th>대상 DB</th>
            <th>다음 실행 예정</th>
            <th>마지막 실행(캐시, 이력 아님)</th>
            <th>작업</th>
          </tr>
        </thead>
        <tbody>
          {schedules.map((schedule) => {
            const lastResult = lastResults[schedule.id];
            return (
              <tr key={schedule.id}>
                <td>{schedule.name}</td>
                <td>
                  <input type="checkbox" checked={schedule.enabled} onChange={() => handleToggleEnabled(schedule)} />
                </td>
                <td>
                  <code>{schedule.cronExpression}</code>
                </td>
                <td>
                  {schedule.targetDbConfigs.map((config) => (
                    <span key={config.id} className="badge-chip">
                      {config.name}
                    </span>
                  ))}
                </td>
                <td>{schedule.nextFireTime ?? "-"}</td>
                <td>
                  {lastResult ? (
                    <span className={statusClassName(lastResult.result.status)}>
                      {statusLabel(lastResult.result.status)}
                    </span>
                  ) : (
                    <span className="muted">없음</span>
                  )}
                </td>
                <td className="actions">
                  <button type="button" onClick={() => handleRunNow(schedule)} disabled={runningId === schedule.id}>
                    {runningId === schedule.id ? "실행 중..." : "지금 실행"}
                  </button>
                  <Link to={`/schedules/${schedule.id}/edit`}>수정</Link>
                  <Link to={`/schedules/${schedule.id}/last-result`}>최근 결과</Link>
                  <button type="button" onClick={() => handleDelete(schedule)}>
                    삭제
                  </button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>

      {activeRunResult && (
        <RunResultModal
          scheduleName={activeRunResult.schedule.name}
          result={activeRunResult.result}
          onClose={() => setActiveRunResult(null)}
        />
      )}
    </div>
  );
}
