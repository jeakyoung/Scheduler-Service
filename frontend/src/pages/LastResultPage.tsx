import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { scheduleApi } from "../api/schedules";
import type { RunResult } from "../api/types";

/** PLAN.md §7 item 5 - DisplayOutputStore/ExecutionResult 인메모리 캐시 열람, 영속 이력 아님 명시. */
export default function LastResultPage() {
  const { id } = useParams<{ id: string }>();
  const [result, setResult] = useState<RunResult | undefined>(undefined);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    scheduleApi
      .lastResult(Number(id))
      .then(setResult)
      .catch(() => setError("결과를 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [id]);

  return (
    <div>
      <p>
        <Link to="/schedules">← 스케줄 목록으로</Link>
      </p>
      <h1>최근 실행 결과</h1>
      <p className="muted">
        이 화면은 프로세스 기동 이후의 최신 실행 1건만 보여주는 인메모리 캐시입니다 — 영속 이력이 아닙니다(재시작하면 사라집니다).
      </p>

      {loading && <p>불러오는 중...</p>}
      {error && <p className="error-text">{error}</p>}

      {!loading && !error && !result && <p>아직 실행된 적이 없습니다.</p>}

      {result && (
        <div>
          <p>
            상태:{" "}
            <strong className={result.result.status === "SUCCESS" ? "status-success" : "status-failure"}>
              {result.result.status === "SUCCESS" ? "성공" : "실패"}
            </strong>
          </p>
          <p>요약: {result.result.summary}</p>
          {result.result.errorMessage && <p className="error-text">에러: {result.result.errorMessage}</p>}
          <p className="muted">
            트리거: {result.result.triggerType} / 시작: {result.result.startedAt} / 종료: {result.result.finishedAt}
          </p>

          {result.displayOutput && (
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    {result.displayOutput.columns.map((column) => (
                      <th key={column}>{column}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {result.displayOutput.rows.map((row, index) => (
                    <tr key={index}>
                      {result.displayOutput!.columns.map((column) => (
                        <td key={column}>{String(row[column] ?? "")}</td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
