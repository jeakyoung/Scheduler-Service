import type { RunResult } from "../api/types";

interface Props {
  scheduleName: string;
  result: RunResult;
  onClose: () => void;
}

/** PLAN.md §7 item 4: "RunResult 컴포넌트/모달" - not a routed page, shown inline from ScheduleListPage. */
export default function RunResultModal({ scheduleName, result, onClose }: Props) {
  const { result: execution, displayOutput } = result;

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true">
      <div className="modal-box">
        <h2>실행 결과 — {scheduleName}</h2>
        <p>
          상태:{" "}
          <strong className={execution.status === "SUCCESS" ? "status-success" : "status-failure"}>
            {execution.status === "SUCCESS" ? "성공" : "실패"}
          </strong>
        </p>
        <p>요약: {execution.summary}</p>
        {execution.errorMessage && <p className="error-text">에러: {execution.errorMessage}</p>}
        <p className="muted">
          시작: {execution.startedAt} / 종료: {execution.finishedAt} ({execution.triggerType})
        </p>

        {displayOutput && (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  {displayOutput.columns.map((column) => (
                    <th key={column}>{column}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {displayOutput.rows.map((row, index) => (
                  <tr key={index}>
                    {displayOutput.columns.map((column) => (
                      <td key={column}>{String(row[column] ?? "")}</td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <button type="button" onClick={onClose}>
          닫기
        </button>
      </div>
    </div>
  );
}
