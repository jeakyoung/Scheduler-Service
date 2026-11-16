import type { RunResult } from "../api/types";

interface Props {
  scheduleName: string;
  result: RunResult;
  onClose: () => void;
}

/** PLAN.md §7 item 4: "RunResult 컴포넌트/모달" - not a routed page, shown inline from ScheduleListPage. */
export default function RunResultModal({ scheduleName, result, onClose }: Props) {
  const { result: execution, displayOutputs } = result;

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

        {/* One table per target DB - never merged (PLAN.md §3.3/§5). T20 adds a fuller
            per-target-DB section (status badge, summary/errorMessage) around this. */}
        {displayOutputs.map((displayOutput) => (
          <div key={displayOutput.targetDbConfigId} className="table-scroll">
            <h3>{displayOutput.targetDbConfigName}</h3>
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
        ))}

        <button type="button" onClick={onClose}>
          닫기
        </button>
      </div>
    </div>
  );
}
