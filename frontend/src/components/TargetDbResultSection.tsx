import type { DisplayOutput, TargetDbExecutionResult } from "../api/types";
import { statusClassName, statusLabel } from "./statusBadge";

interface Props {
  targetResult: TargetDbExecutionResult;
  displayOutput: DisplayOutput | undefined;
}

/**
 * One target DB's result (PLAN.md §3.1b/§3.3/§7) - name + status badge + summary/errorMessage,
 * and the DISPLAY_ONLY table if this target DB actually produced one. Shared by RunResultModal
 * and LastResultPage so both render target DBs identically, never merging their rows (§5).
 */
export default function TargetDbResultSection({ targetResult, displayOutput }: Props) {
  return (
    <div className="target-db-section">
      <p>
        <strong>{targetResult.targetDbConfigName}</strong>{" "}
        <span className={statusClassName(targetResult.status)}>{statusLabel(targetResult.status)}</span>
      </p>
      <p className="muted">{targetResult.summary}</p>
      {targetResult.errorMessage && <p className="error-text">에러: {targetResult.errorMessage}</p>}

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
    </div>
  );
}
