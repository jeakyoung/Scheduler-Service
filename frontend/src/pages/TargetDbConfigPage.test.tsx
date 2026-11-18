import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import TargetDbConfigPage from "./TargetDbConfigPage";
import { targetDbConfigApi } from "../api/targetDbConfigs";
import type { TargetDbConfig } from "../api/types";

// PLAN.md §7 item 6 (2026-11-18)/TASKS.md T29 DoD: the "연결 테스트" button (unsaved form
// input) and each row's "테스트" button (saved config, by id) must drive independent,
// non-persisted state - never auto-called on load, never shared across rows.
vi.mock("../api/targetDbConfigs", () => ({
  targetDbConfigApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
    testConnection: vi.fn(),
    testConnectionById: vi.fn(),
  },
}));

const configs: TargetDbConfig[] = [
  { id: 1, name: "A사 DB", jdbcUrl: "jdbc:sqlserver://a;", username: "u1", maskedPassword: "****", createdAt: null, updatedAt: null },
  { id: 2, name: "B사 DB", jdbcUrl: "jdbc:sqlserver://b;", username: "u2", maskedPassword: "****", createdAt: null, updatedAt: null },
];

describe("TargetDbConfigPage connection test (T29)", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("starts every row as 미확인 (unknown) without calling testConnectionById automatically", async () => {
    vi.mocked(targetDbConfigApi.list).mockResolvedValue(configs);

    render(<TargetDbConfigPage />);

    await waitFor(() => expect(screen.getByText("A사 DB")).toBeInTheDocument());

    expect(screen.getAllByText("미확인")).toHaveLength(2);
    expect(targetDbConfigApi.testConnectionById).not.toHaveBeenCalled();
  });

  it("clicking one row's 테스트 button updates only that row's badge", async () => {
    vi.mocked(targetDbConfigApi.list).mockResolvedValue(configs);
    vi.mocked(targetDbConfigApi.testConnectionById).mockImplementation(async (id: number) =>
      id === 1 ? { success: true, message: "ok" } : { success: false, message: "연결에 실패했습니다." },
    );

    render(<TargetDbConfigPage />);
    await waitFor(() => expect(screen.getByText("A사 DB")).toBeInTheDocument());

    const rowA = screen.getByText("A사 DB").closest("tr")!;
    fireEvent.click(within(rowA).getByRole("button", { name: "테스트" }));

    await waitFor(() => expect(within(rowA).getByText("연결 성공")).toBeInTheDocument());
    expect(targetDbConfigApi.testConnectionById).toHaveBeenCalledWith(1);
    expect(targetDbConfigApi.testConnectionById).not.toHaveBeenCalledWith(2);

    // The untouched row is still showing "미확인", not affected by row A's result.
    const rowB = screen.getByText("B사 DB").closest("tr")!;
    expect(within(rowB).getByText("미확인")).toBeInTheDocument();
  });

  it("shows a failure message on a row without leaking into other rows", async () => {
    vi.mocked(targetDbConfigApi.list).mockResolvedValue(configs);
    vi.mocked(targetDbConfigApi.testConnectionById).mockResolvedValue({
      success: false,
      message: "인증에 실패했습니다. 사용자명 또는 비밀번호를 확인하세요.",
    });

    render(<TargetDbConfigPage />);
    await waitFor(() => expect(screen.getByText("B사 DB")).toBeInTheDocument());

    const rowB = screen.getByText("B사 DB").closest("tr")!;
    fireEvent.click(within(rowB).getByRole("button", { name: "테스트" }));

    await waitFor(() =>
      expect(within(rowB).getByText(/연결 실패: 인증에 실패했습니다/)).toBeInTheDocument(),
    );
  });

  it("tests the create form's unsaved input without persisting anything", async () => {
    vi.mocked(targetDbConfigApi.list).mockResolvedValue([]);
    vi.mocked(targetDbConfigApi.testConnection).mockResolvedValue({ success: true, message: "ok" });

    render(<TargetDbConfigPage />);
    await waitFor(() => expect(screen.getByText(/등록된 대상 DB가 없습니다/)).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: "+ 새 대상 DB" }));
    fireEvent.change(screen.getByLabelText("이름"), { target: { value: "신규 테스트 대상" } });
    fireEvent.change(screen.getByLabelText("JDBC URL"), { target: { value: "jdbc:sqlserver://new;" } });
    fireEvent.change(screen.getByLabelText("사용자명"), { target: { value: "new_user" } });
    fireEvent.change(screen.getByLabelText(/^비밀번호/), { target: { value: "new-secret-pw" } });

    fireEvent.click(screen.getByRole("button", { name: "연결 테스트" }));

    await waitFor(() => expect(screen.getByText("연결 성공")).toBeInTheDocument());
    expect(targetDbConfigApi.testConnection).toHaveBeenCalledWith({
      name: "신규 테스트 대상",
      jdbcUrl: "jdbc:sqlserver://new;",
      username: "new_user",
      password: "new-secret-pw",
    });
    expect(targetDbConfigApi.create).not.toHaveBeenCalled();
  });
});

/**
 * TASKS.T30 DoD explicitly asks for the 미확인→테스트중→성공/실패 *transition* to be
 * rendered correctly, not just the before/after states (already covered above by T29's
 * tests) - these use a manually-resolved promise so the pending "테스트 중..." state can be
 * observed before it flips to its final value.
 */
describe("TargetDbConfigPage connection test state transitions (T30)", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("row button transitions 미확인 → 테스트 중 → 연결 성공", async () => {
    vi.mocked(targetDbConfigApi.list).mockResolvedValue(configs);
    let resolveTest!: (value: { success: boolean; message: string }) => void;
    vi.mocked(targetDbConfigApi.testConnectionById).mockImplementation(
      () => new Promise((resolve) => (resolveTest = resolve)),
    );

    render(<TargetDbConfigPage />);
    await waitFor(() => expect(screen.getByText("A사 DB")).toBeInTheDocument());
    const rowA = screen.getByText("A사 DB").closest("tr")!;
    expect(within(rowA).getByText("미확인")).toBeInTheDocument();

    fireEvent.click(within(rowA).getByRole("button", { name: "테스트" }));
    await waitFor(() => expect(within(rowA).getByText(/테스트 중/)).toBeInTheDocument());
    expect(within(rowA).queryByText("미확인")).not.toBeInTheDocument();

    resolveTest({ success: true, message: "ok" });
    await waitFor(() => expect(within(rowA).getByText("연결 성공")).toBeInTheDocument());
    expect(within(rowA).queryByText(/테스트 중/)).not.toBeInTheDocument();
  });

  it("form button transitions idle → 테스트 중 → 연결 실패: {message}", async () => {
    vi.mocked(targetDbConfigApi.list).mockResolvedValue([]);
    let resolveTest!: (value: { success: boolean; message: string }) => void;
    vi.mocked(targetDbConfigApi.testConnection).mockImplementation(
      () => new Promise((resolve) => (resolveTest = resolve)),
    );

    render(<TargetDbConfigPage />);
    await waitFor(() => expect(screen.getByText(/등록된 대상 DB가 없습니다/)).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: "+ 새 대상 DB" }));
    fireEvent.change(screen.getByLabelText("이름"), { target: { value: "X" } });
    fireEvent.change(screen.getByLabelText("JDBC URL"), { target: { value: "jdbc:sqlserver://x;" } });
    fireEvent.change(screen.getByLabelText("사용자명"), { target: { value: "u" } });
    fireEvent.change(screen.getByLabelText(/^비밀번호/), { target: { value: "pw" } });

    expect(screen.queryByText(/테스트 중|연결 성공|연결 실패/)).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "연결 테스트" }));
    await waitFor(() => expect(screen.getByText(/테스트 중/)).toBeInTheDocument());

    resolveTest({ success: false, message: "타임아웃" });
    await waitFor(() => expect(screen.getByText("연결 실패: 타임아웃")).toBeInTheDocument());
    expect(screen.queryByText(/테스트 중/)).not.toBeInTheDocument();
  });
});

/**
 * Bug report (screenshot): a long jdbcUrl wrapped across multiple lines and pushed the
 * 수정/삭제/테스트 buttons in the same row onto a second line. Fixed with index.css's
 * .cell-truncate (max-width + overflow:hidden + text-overflow:ellipsis + white-space:nowrap)
 * plus a title="" attribute for the full value on hover. There's no browser automation in this
 * environment (same constraint as T12/T20/T26 etc.) to visually confirm no wrapping occurs, so
 * this checks what jsdom/RTL actually can: the class is applied, the title attribute carries
 * the full untruncated value (CSS ellipsis is a rendering effect only - it doesn't touch the
 * DOM text), and the row's buttons remain present and queryable (i.e. nothing about the long
 * value broke the row's structure).
 */
describe("TargetDbConfigPage long jdbcUrl layout fix", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("truncates a long jdbcUrl with a title tooltip instead of letting it wrap", async () => {
    const longJdbcUrl =
      "jdbc:sqlserver://very-long-erp-hostname.internal.example.com;databaseName=erp_production;encrypt=true;trustServerCertificate=false;loginTimeout=30;";
    expect(longJdbcUrl.length).toBeGreaterThan(80);

    vi.mocked(targetDbConfigApi.list).mockResolvedValue([
      { id: 1, name: "Long URL target", jdbcUrl: longJdbcUrl, username: "u1", maskedPassword: "****", createdAt: null, updatedAt: null },
    ]);

    render(<TargetDbConfigPage />);
    await waitFor(() => expect(screen.getByText("Long URL target")).toBeInTheDocument());

    const row = screen.getByText("Long URL target").closest("tr")!;
    const urlCell = within(row).getByTitle(longJdbcUrl);
    expect(urlCell.tagName).toBe("TD");
    expect(urlCell).toHaveClass("cell-truncate");
    expect(urlCell).toHaveTextContent(longJdbcUrl);

    // The row's own buttons must still be present and in the same row, not disturbed by the
    // long value in the sibling cell.
    expect(within(row).getByRole("button", { name: "수정" })).toBeInTheDocument();
    expect(within(row).getByRole("button", { name: "삭제" })).toBeInTheDocument();
    expect(within(row).getByRole("button", { name: "테스트" })).toBeInTheDocument();
  });
});
