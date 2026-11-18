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
