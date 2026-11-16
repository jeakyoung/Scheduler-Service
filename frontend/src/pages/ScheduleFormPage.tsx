import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { scheduleApi } from "../api/schedules";
import { targetDbConfigApi } from "../api/targetDbConfigs";
import { credentialApi } from "../api/credentials";
import type {
  ActionType,
  ApiCallActionConfig,
  Credential,
  DisplayOnlyActionConfig,
  ScheduleParameterInput,
  TargetDbConfig,
} from "../api/types";
import ActionConfigEditor from "../components/ActionConfigEditor";
import ParameterEditor from "../components/ParameterEditor";
import CronBuilder from "../components/CronBuilder";

const EMPTY_API_CALL: ApiCallActionConfig = {
  method: "POST",
  url: "",
  headers: {},
  groupingMode: "PER_ROW",
  groupByField: null,
  bodyTemplate: "",
};

const EMPTY_DISPLAY_ONLY: DisplayOnlyActionConfig = {
  displayFormat: "TABLE",
  columns: null,
};

/** PLAN.md §7 item 3 - create/edit 공용, action_type에 따라 입력 영역 전환 + 파라미터 편집기. */
export default function ScheduleFormPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const isEditing = id !== undefined;

  const [name, setName] = useState("");
  const [enabled, setEnabled] = useState(false);
  const [cronExpression, setCronExpression] = useState("");
  const [timezone, setTimezone] = useState("Asia/Seoul");
  const [spName, setSpName] = useState("");
  const [targetDbConfigId, setTargetDbConfigId] = useState<number | "">("");
  const [actionType, setActionType] = useState<ActionType>("DISPLAY_ONLY");
  const [apiCallConfig, setApiCallConfig] = useState<ApiCallActionConfig>(EMPTY_API_CALL);
  const [displayOnlyConfig, setDisplayOnlyConfig] = useState<DisplayOnlyActionConfig>(EMPTY_DISPLAY_ONLY);
  const [description, setDescription] = useState("");
  const [parameters, setParameters] = useState<ScheduleParameterInput[]>([]);

  const [targetDbConfigs, setTargetDbConfigs] = useState<TargetDbConfig[]>([]);
  const [credentials, setCredentials] = useState<Credential[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    async function load() {
      setLoading(true);
      try {
        const [dbConfigs, creds] = await Promise.all([targetDbConfigApi.list(), credentialApi.list()]);
        setTargetDbConfigs(dbConfigs);
        setCredentials(creds);

        if (isEditing) {
          const schedule = await scheduleApi.get(Number(id));
          setName(schedule.name);
          setEnabled(schedule.enabled);
          setCronExpression(schedule.cronExpression);
          setTimezone(schedule.timezone);
          setSpName(schedule.spName);
          // T20 replaces this single-select with a real multi-select
          // (TargetDbConfigMultiSelect) sending the full targetDbConfigIds array - for now
          // this just shows/edits the first configured target DB.
          setTargetDbConfigId(schedule.targetDbConfigIds[0] ?? "");
          setActionType(schedule.actionType);
          setDescription(schedule.description ?? "");
          setParameters(
            schedule.parameters.map((p) => ({
              paramOrder: p.paramOrder,
              paramName: p.paramName,
              dataType: p.dataType,
              valueSource: p.valueSource,
              literalValue: p.literalValue,
            }))
          );
          if (schedule.actionType === "API_CALL") {
            setApiCallConfig(schedule.actionConfig as ApiCallActionConfig);
          } else {
            setDisplayOnlyConfig(schedule.actionConfig as DisplayOnlyActionConfig);
          }
        } else if (dbConfigs.length > 0) {
          setTargetDbConfigId(dbConfigs[0].id);
        }
      } catch {
        setError("데이터를 불러오지 못했습니다.");
      } finally {
        setLoading(false);
      }
    }
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (targetDbConfigId === "") {
      setError("대상 DB 설정을 선택해 주세요.");
      return;
    }

    setSaving(true);
    setError(null);
    try {
      const input = {
        name,
        enabled,
        cronExpression,
        timezone,
        spName,
        targetDbConfigIds: [targetDbConfigId as number],
        actionType,
        actionConfig: actionType === "API_CALL" ? apiCallConfig : displayOnlyConfig,
        description: description || null,
      };

      const saved = isEditing ? await scheduleApi.update(Number(id), input) : await scheduleApi.create(input);
      await scheduleApi.replaceParameters(saved.id, parameters);

      navigate("/schedules");
    } catch {
      setError("저장에 실패했습니다. 입력값을 확인해 주세요.");
    } finally {
      setSaving(false);
    }
  }

  if (loading) return <p>불러오는 중...</p>;

  return (
    <div>
      <h1>{isEditing ? "스케줄 수정" : "새 스케줄"}</h1>
      <form onSubmit={handleSubmit} className="schedule-form">
        <label>
          이름
          <input value={name} onChange={(e) => setName(e.target.value)} required />
        </label>

        <label>
          <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
          활성화 (Quartz에 즉시 등록됨)
        </label>

        <CronBuilder value={cronExpression} onChange={setCronExpression} />

        <label>
          타임존
          <input value={timezone} onChange={(e) => setTimezone(e.target.value)} required />
        </label>

        <label>
          대상 DB 설정
          <select
            value={targetDbConfigId}
            onChange={(e) => setTargetDbConfigId(e.target.value === "" ? "" : Number(e.target.value))}
            required
          >
            <option value="" disabled>
              선택하세요
            </option>
            {targetDbConfigs.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </label>

        <label>
          SP 이름
          <input value={spName} onChange={(e) => setSpName(e.target.value)} placeholder="SP_SAMPLE_LIST" required />
        </label>

        <label>
          동작 유형
          <select value={actionType} onChange={(e) => setActionType(e.target.value as ActionType)}>
            <option value="DISPLAY_ONLY">DISPLAY_ONLY (웹에 결과만 표시)</option>
            <option value="API_CALL">API_CALL (외부 API 호출)</option>
          </select>
        </label>

        <ActionConfigEditor
          actionType={actionType}
          apiCallConfig={apiCallConfig}
          displayOnlyConfig={displayOnlyConfig}
          credentials={credentials}
          onApiCallChange={setApiCallConfig}
          onDisplayOnlyChange={setDisplayOnlyConfig}
        />

        <label>
          설명
          <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={2} />
        </label>

        <h2>파라미터</h2>
        <ParameterEditor parameters={parameters} onChange={setParameters} />

        {error && <p className="error-text">{error}</p>}

        <div className="form-actions">
          <button type="submit" disabled={saving}>
            {saving ? "저장 중..." : "저장"}
          </button>
          <button type="button" onClick={() => navigate("/schedules")}>
            취소
          </button>
        </div>
      </form>
    </div>
  );
}
