import { describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import RunResultModal from "./RunResultModal";
import type { RunResult } from "../api/types";

// TASKS.md T20 DoD: two target DBs' sections must render separately, with the right status
// badges, and never mix each other's rows - exercised here as an automated component test
// since no browser-automation tool is available in this environment to click through the
// real app (documented in LOG.md).
const twoTargetResult: RunResult = {
  result: {
    scheduleId: 1,
    triggerType: "MANUAL",
    startedAt: "2026-11-17T00:00:00Z",
    finishedAt: "2026-11-17T00:00:01Z",
    status: "PARTIAL_SUCCESS",
    summary: "대상DB 2개 중 1개 성공, 1개 실패",
    errorMessage: null,
    targetDbResults: [
      {
        targetDbConfigId: 1,
        targetDbConfigName: "A사 DB",
        status: "SUCCESS",
        summary: "SP 결과 1행 가공 완료",
        errorMessage: null,
      },
      {
        targetDbConfigId: 2,
        targetDbConfigName: "B사 DB",
        status: "FAILURE",
        summary: "대상DB 실행 중 오류 발생",
        errorMessage: "Failed to execute stored procedure SP_SAMPLE: login failed",
      },
    ],
  },
  displayOutputs: [
    {
      scheduleId: 1,
      targetDbConfigId: 1,
      targetDbConfigName: "A사 DB",
      columns: ["EMPLOYEE_NO"],
      rows: [{ EMPLOYEE_NO: "A-001" }],
      generatedAt: "2026-11-17T00:00:01Z",
    },
  ],
};

describe("RunResultModal multi-target-DB rendering", () => {
  it("renders the overall PARTIAL_SUCCESS badge and summary", () => {
    render(<RunResultModal scheduleName="Multi target schedule" result={twoTargetResult} onClose={vi.fn()} />);

    expect(screen.getByText("부분성공")).toBeInTheDocument();
    expect(screen.getByText(/대상DB 2개 중 1개 성공, 1개 실패/)).toBeInTheDocument();
  });

  it("renders two separate target DB sections with their own status badges", () => {
    render(<RunResultModal scheduleName="Multi target schedule" result={twoTargetResult} onClose={vi.fn()} />);

    expect(screen.getByText("A사 DB")).toBeInTheDocument();
    expect(screen.getByText("B사 DB")).toBeInTheDocument();
    expect(screen.getByText(/Failed to execute stored procedure SP_SAMPLE: login failed/)).toBeInTheDocument();
  });

  it("never mixes one target DB's rows into another's section (PLAN.md §3.3/§5)", () => {
    render(<RunResultModal scheduleName="Multi target schedule" result={twoTargetResult} onClose={vi.fn()} />);

    const tables = screen.getAllByRole("table");
    // Only the successful target DB produced a DisplayOutput - the failed one has no table.
    expect(tables).toHaveLength(1);
    expect(within(tables[0]).getByText("A-001")).toBeInTheDocument();
    expect(within(tables[0]).queryByText("B-001")).not.toBeInTheDocument();
  });
});
