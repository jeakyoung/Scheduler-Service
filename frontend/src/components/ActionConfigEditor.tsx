import type { ApiCallActionConfig, Credential, DisplayOnlyActionConfig, GroupingMode } from "../api/types";

interface Props {
  actionType: "API_CALL" | "DISPLAY_ONLY";
  apiCallConfig: ApiCallActionConfig;
  displayOnlyConfig: DisplayOnlyActionConfig;
  credentials: Credential[];
  onApiCallChange: (config: ApiCallActionConfig) => void;
  onDisplayOnlyChange: (config: DisplayOnlyActionConfig) => void;
}

/** PLAN.md §5 action_config shapes + §7 ScheduleForm's "action_type에 따라 입력 영역 전환". */
export default function ActionConfigEditor({
  actionType,
  apiCallConfig,
  displayOnlyConfig,
  credentials,
  onApiCallChange,
  onDisplayOnlyChange,
}: Props) {
  if (actionType === "API_CALL") {
    const headerEntries = Object.entries(apiCallConfig.headers ?? {});

    function updateHeader(index: number, key: string, value: string) {
      const next = [...headerEntries];
      next[index] = [key, value];
      onApiCallChange({ ...apiCallConfig, headers: Object.fromEntries(next) });
    }
    function addHeader() {
      onApiCallChange({ ...apiCallConfig, headers: { ...apiCallConfig.headers, "": "" } });
    }
    function removeHeader(index: number) {
      onApiCallChange({ ...apiCallConfig, headers: Object.fromEntries(headerEntries.filter((_, i) => i !== index)) });
    }

    return (
      <fieldset>
        <legend>API_CALL 설정</legend>

        <label>
          Method
          <select value={apiCallConfig.method} onChange={(e) => onApiCallChange({ ...apiCallConfig, method: e.target.value })}>
            <option value="POST">POST</option>
            <option value="PUT">PUT</option>
            <option value="PATCH">PATCH</option>
            <option value="GET">GET</option>
            <option value="DELETE">DELETE</option>
          </select>
        </label>

        <label>
          URL
          <input
            value={apiCallConfig.url}
            onChange={(e) => onApiCallChange({ ...apiCallConfig, url: e.target.value })}
            placeholder="https://notify.example.com/api/push"
          />
        </label>

        <div>
          <p>
            헤더 (값에 <code>{"${cred:NAME}"}</code> 형태로 자격증명 참조 가능 - 아래 목록에서 선택)
          </p>
          {headerEntries.map(([key, value], index) => (
            <div className="kv-row" key={index}>
              <input value={key} placeholder="헤더명" onChange={(e) => updateHeader(index, e.target.value, value)} />
              <input
                value={value}
                placeholder="값 또는 ${cred:NAME}"
                list="credential-names"
                onChange={(e) => updateHeader(index, key, e.target.value)}
              />
              <button type="button" onClick={() => removeHeader(index)}>
                삭제
              </button>
            </div>
          ))}
          <datalist id="credential-names">
            {credentials.map((c) => (
              <option key={c.id} value={"${cred:" + c.name + "}"} />
            ))}
          </datalist>
          <button type="button" onClick={addHeader}>
            + 헤더 추가
          </button>
        </div>

        <label>
          그룹핑 모드
          <select
            value={apiCallConfig.groupingMode}
            onChange={(e) => onApiCallChange({ ...apiCallConfig, groupingMode: e.target.value as GroupingMode })}
          >
            <option value="PER_ROW">PER_ROW (SP 결과 행마다 1회 호출)</option>
            <option value="GROUP_BY">GROUP_BY (지정 컬럼 기준 그룹당 1회 호출)</option>
          </select>
        </label>

        {apiCallConfig.groupingMode === "GROUP_BY" && (
          <label>
            Group By 컬럼
            <input
              value={apiCallConfig.groupByField ?? ""}
              onChange={(e) => onApiCallChange({ ...apiCallConfig, groupByField: e.target.value })}
              placeholder="EA_TITLE"
            />
          </label>
        )}

        <label>
          Body 템플릿 (<code>{"${COLUMN}"}</code> = SP 결과 컬럼값, <code>{"${cred:NAME}"}</code> = 자격증명으로 치환)
          <textarea
            value={apiCallConfig.bodyTemplate}
            onChange={(e) => onApiCallChange({ ...apiCallConfig, bodyTemplate: e.target.value })}
            rows={4}
            placeholder={'{"empList":["${EMPLOYEE_NO}"]}'}
          />
        </label>
      </fieldset>
    );
  }

  return (
    <fieldset>
      <legend>DISPLAY_ONLY 설정</legend>
      <label>
        표시 형식
        <input
          value={displayOnlyConfig.displayFormat}
          onChange={(e) => onDisplayOnlyChange({ ...displayOnlyConfig, displayFormat: e.target.value })}
          placeholder="TABLE"
        />
      </label>
      <label>
        표시 컬럼 (쉼표로 구분, 비워두면 SP가 반환한 모든 컬럼 표시)
        <input
          value={(displayOnlyConfig.columns ?? []).join(",")}
          onChange={(e) =>
            onDisplayOnlyChange({
              ...displayOnlyConfig,
              columns: e.target.value.trim() === "" ? null : e.target.value.split(",").map((c) => c.trim()),
            })
          }
          placeholder="EMPLOYEE_NO,EA_TITLE,EXE_SEQ"
        />
      </label>
    </fieldset>
  );
}
