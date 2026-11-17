import { useEffect, useState } from "react";
import {
  ALL_DAYS,
  buildCron,
  parseCron,
  type DayOfWeek,
  type IntervalPattern,
  type RecurrencePattern,
} from "./cronPatterns";

const DAY_LABELS: Record<DayOfWeek, string> = {
  MON: "월",
  TUE: "화",
  WED: "수",
  THU: "목",
  FRI: "금",
  SAT: "토",
  SUN: "일",
};

const PATTERN_LABELS: Record<RecurrencePattern["type"], string> = {
  DAILY: "일별",
  MONTHLY: "월별",
  YEARLY: "년별",
  WEEKLY: "요일반복",
  INTERVAL: "간격반복",
  ONCE: "1회성",
  CUSTOM: "고급: 직접 cron 입력",
};

interface Props {
  /** The schedule's current cronExpression - used only to seed initial state on mount. */
  value: string;
  onChange: (cron: string) => void;
}

/**
 * PLAN.md §9 - structured recurrence input that generates a Quartz cron string, with an
 * always-available "고급: 직접 cron 입력" fallback (§9.3). The backend still re-validates the
 * final string with CronExpression.isValidExpression regardless of which mode produced it
 * (§9.1) - this component's only job is producing/parsing the string, never bypassing that.
 */
export default function CronBuilder({ value, onChange }: Props) {
  // A brand-new schedule (empty value) starts at pattern selection, not advanced/CUSTOM mode
  // (TASKS.md T19) - only an existing cron that doesn't match any of the 6 shapes falls back
  // to CUSTOM (PLAN.md §9.3).
  const [pattern, setPattern] = useState<RecurrencePattern>(
    () => parseCron(value) ?? (value === "" ? { type: "DAILY", hour: 9, minute: 0 } : { type: "CUSTOM", cron: value }),
  );

  useEffect(() => {
    onChange(buildCron(pattern));
    // Only re-run when the structured pattern itself changes - onChange is a parent setState
    // function and must not be a dependency here, or every parent render would re-trigger this.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pattern]);

  function switchType(type: RecurrencePattern["type"]) {
    setPattern(defaultPatternFor(type, pattern));
  }

  return (
    <fieldset>
      <legend>반복 주기</legend>
      <label>
        유형
        <select value={pattern.type} onChange={(e) => switchType(e.target.value as RecurrencePattern["type"])}>
          {(Object.keys(PATTERN_LABELS) as RecurrencePattern["type"][]).map((type) => (
            <option key={type} value={type}>
              {PATTERN_LABELS[type]}
            </option>
          ))}
        </select>
      </label>

      {pattern.type === "DAILY" && (
        <div className="kv-row">
          <TimeInputs
            hour={pattern.hour}
            minute={pattern.minute}
            onChange={(hour, minute) => setPattern({ ...pattern, hour, minute })}
          />
        </div>
      )}

      {pattern.type === "MONTHLY" && (
        <div className="kv-row">
          <label>
            일(1~31)
            <input
              type="number"
              min={1}
              max={31}
              value={pattern.day}
              onChange={(e) => setPattern({ ...pattern, day: Number(e.target.value) })}
            />
          </label>
          <TimeInputs
            hour={pattern.hour}
            minute={pattern.minute}
            onChange={(hour, minute) => setPattern({ ...pattern, hour, minute })}
          />
        </div>
      )}

      {pattern.type === "YEARLY" && (
        <div className="kv-row">
          <label>
            월(1~12)
            <input
              type="number"
              min={1}
              max={12}
              value={pattern.month}
              onChange={(e) => setPattern({ ...pattern, month: Number(e.target.value) })}
            />
          </label>
          <label>
            일(1~31)
            <input
              type="number"
              min={1}
              max={31}
              value={pattern.day}
              onChange={(e) => setPattern({ ...pattern, day: Number(e.target.value) })}
            />
          </label>
          <TimeInputs
            hour={pattern.hour}
            minute={pattern.minute}
            onChange={(hour, minute) => setPattern({ ...pattern, hour, minute })}
          />
        </div>
      )}

      {pattern.type === "WEEKLY" && (
        <div className="kv-row">
          <DayPicker days={pattern.days} onChange={(days) => setPattern({ ...pattern, days })} allowEmpty={false} />
          <TimeInputs
            hour={pattern.hour}
            minute={pattern.minute}
            onChange={(hour, minute) => setPattern({ ...pattern, hour, minute })}
          />
        </div>
      )}

      {pattern.type === "INTERVAL" && <IntervalForm pattern={pattern} onChange={setPattern} />}

      {pattern.type === "ONCE" && (
        <div className="kv-row">
          <label>
            연
            <input
              type="number"
              value={pattern.year}
              onChange={(e) => setPattern({ ...pattern, year: Number(e.target.value) })}
            />
          </label>
          <label>
            월(1~12)
            <input
              type="number"
              min={1}
              max={12}
              value={pattern.month}
              onChange={(e) => setPattern({ ...pattern, month: Number(e.target.value) })}
            />
          </label>
          <label>
            일(1~31)
            <input
              type="number"
              min={1}
              max={31}
              value={pattern.day}
              onChange={(e) => setPattern({ ...pattern, day: Number(e.target.value) })}
            />
          </label>
          <TimeInputs
            hour={pattern.hour}
            minute={pattern.minute}
            onChange={(hour, minute) => setPattern({ ...pattern, hour, minute })}
          />
        </div>
      )}

      {pattern.type === "CUSTOM" && (
        <label>
          Cron 표현식 (Quartz 형식, 요일 지정 시 일(day-of-month)은 <code>?</code>)
          <input
            value={pattern.cron}
            onChange={(e) => setPattern({ type: "CUSTOM", cron: e.target.value })}
            placeholder="0 0/30 * ? * MON-FRI"
          />
        </label>
      )}

      <p className="muted">생성된 cron: <code>{buildCron(pattern)}</code></p>
    </fieldset>
  );
}

function defaultPatternFor(type: RecurrencePattern["type"], previous: RecurrencePattern): RecurrencePattern {
  const hour = "hour" in previous ? previous.hour : 9;
  const minute = "minute" in previous ? previous.minute : 0;
  switch (type) {
    case "DAILY":
      return { type: "DAILY", hour, minute };
    case "MONTHLY":
      return { type: "MONTHLY", day: 1, hour, minute };
    case "YEARLY":
      return { type: "YEARLY", month: 1, day: 1, hour, minute };
    case "WEEKLY":
      return { type: "WEEKLY", days: ["MON"], hour, minute };
    case "INTERVAL":
      return { type: "INTERVAL", startHour: 9, startMinute: 0, endHour: 18, intervalValue: 30, intervalUnit: "MINUTE", days: [] };
    case "ONCE":
      return { type: "ONCE", year: new Date().getFullYear(), month: 1, day: 1, hour, minute };
    case "CUSTOM":
      return { type: "CUSTOM", cron: buildCron(previous) };
  }
}

function TimeInputs({
  hour,
  minute,
  onChange,
}: {
  hour: number;
  minute: number;
  onChange: (hour: number, minute: number) => void;
}) {
  return (
    <>
      <label>
        시(0~23)
        <input type="number" min={0} max={23} value={hour} onChange={(e) => onChange(Number(e.target.value), minute)} />
      </label>
      <label>
        분(0~59)
        <input type="number" min={0} max={59} value={minute} onChange={(e) => onChange(hour, Number(e.target.value))} />
      </label>
    </>
  );
}

function DayPicker({
  days,
  onChange,
  allowEmpty,
}: {
  days: DayOfWeek[];
  onChange: (days: DayOfWeek[]) => void;
  allowEmpty: boolean;
}) {
  function toggle(day: DayOfWeek) {
    if (days.includes(day)) {
      const next = days.filter((d) => d !== day);
      if (next.length === 0 && !allowEmpty) return;
      onChange(next);
    } else {
      onChange([...days, day]);
    }
  }

  return (
    <div className="checkbox-list-inline">
      {ALL_DAYS.map((day) => (
        <label key={day}>
          <input type="checkbox" checked={days.includes(day)} onChange={() => toggle(day)} />
          {DAY_LABELS[day]}
        </label>
      ))}
    </div>
  );
}

function IntervalForm({
  pattern,
  onChange,
}: {
  pattern: IntervalPattern;
  onChange: (pattern: IntervalPattern) => void;
}) {
  return (
    <div>
      <div className="kv-row">
        <label>
          시작 시각
          <input
            type="time"
            value={`${String(pattern.startHour).padStart(2, "0")}:${String(pattern.startMinute).padStart(2, "0")}`}
            onChange={(e) => {
              const [h, m] = e.target.value.split(":").map(Number);
              onChange({ ...pattern, startHour: h, startMinute: m });
            }}
          />
        </label>
        <label>
          종료 시각(시)
          <input
            type="number"
            min={0}
            max={23}
            value={pattern.endHour}
            onChange={(e) => onChange({ ...pattern, endHour: Number(e.target.value) })}
          />
        </label>
      </div>
      <div className="kv-row">
        <label>
          간격
          <input
            type="number"
            min={1}
            value={pattern.intervalValue}
            onChange={(e) => onChange({ ...pattern, intervalValue: Number(e.target.value) })}
          />
        </label>
        <label>
          간격 단위
          <select
            value={pattern.intervalUnit}
            onChange={(e) => onChange({ ...pattern, intervalUnit: e.target.value as "MINUTE" | "HOUR" })}
          >
            <option value="MINUTE">분</option>
            <option value="HOUR">시간</option>
          </select>
        </label>
      </div>
      <p className="muted">요일 필터(선택 안 하면 매일)</p>
      <DayPicker days={pattern.days} onChange={(days) => onChange({ ...pattern, days })} allowEmpty={true} />
    </div>
  );
}
