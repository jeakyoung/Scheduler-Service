import type { RunResult } from "../api/types";
import { statusClassName, statusLabel } from "./statusBadge";
import TargetDbResultSection from "./TargetDbResultSection";

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
          상태: <strong className={statusClassName(execution.status)}>{statusLabel(execution.status)}</strong>
        </p>
        <p>요약: {execution.summary}</p>
        {execution.errorMessage && <p className="error-text">에러: {execution.errorMessage}</p>}
        <p className="muted">
          시작: {execution.startedAt} / 종료: {execution.finishedAt} ({execution.triggerType})
        </p>

        <h3>대상 DB별 결과</h3>
        {execution.targetDbResults.map((targetResult) => (
          <TargetDbResultSection
            key={targetResult.targetDbConfigId}
            targetResult={targetResult}
            displayOutput={displayOutputs.find((output) => output.targetDbConfigId === targetResult.targetDbConfigId)}
          />
        ))}

        <button type="button" onClick={onClose}>
          닫기
        </button>
      </div>
    </div>
  );
}
