import type { ExecutionStatus } from "../api/types";

export function statusLabel(status: ExecutionStatus): string {
  switch (status) {
    case "SUCCESS":
      return "성공";
    case "PARTIAL_SUCCESS":
      return "부분성공";
    case "FAILURE":
      return "실패";
  }
}

export function statusClassName(status: ExecutionStatus): string {
  switch (status) {
    case "SUCCESS":
      return "status-success";
    case "PARTIAL_SUCCESS":
      return "status-partial";
    case "FAILURE":
      return "status-failure";
  }
}
