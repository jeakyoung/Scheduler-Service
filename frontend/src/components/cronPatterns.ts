// Pure cron generation/parsing logic (PLAN.md §9) - deliberately separate from CronBuilder.tsx
// so it's testable without React. The backend never parses or generates these - it only
// validates the final string with Quartz's CronExpression.isValidExpression (PLAN.md §9.1),
// so this module's correctness is the only thing standing between a user's structured input
// and a valid/invalid Quartz cron string.

export type DayOfWeek = "MON" | "TUE" | "WED" | "THU" | "FRI" | "SAT" | "SUN";

export const ALL_DAYS: DayOfWeek[] = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"];

export interface DailyPattern {
  type: "DAILY";
  hour: number;
  minute: number;
}

export interface MonthlyPattern {
  type: "MONTHLY";
  day: number;
  hour: number;
  minute: number;
}

export interface YearlyPattern {
  type: "YEARLY";
  month: number;
  day: number;
  hour: number;
  minute: number;
}

export interface WeeklyPattern {
  type: "WEEKLY";
  days: DayOfWeek[];
  hour: number;
  minute: number;
}

export interface IntervalPattern {
  type: "INTERVAL";
  startHour: number;
  startMinute: number;
  endHour: number;
  intervalValue: number;
  intervalUnit: "MINUTE" | "HOUR";
  /** Empty = every day of the week (generates "*"). */
  days: DayOfWeek[];
}

export interface OncePattern {
  type: "ONCE";
  year: number;
  month: number;
  day: number;
  hour: number;
  minute: number;
}

export interface CustomPattern {
  type: "CUSTOM";
  cron: string;
}

export type RecurrencePattern =
  | DailyPattern
  | MonthlyPattern
  | YearlyPattern
  | WeeklyPattern
  | IntervalPattern
  | OncePattern
  | CustomPattern;

const NUM = /^\d{1,4}$/;
const DAY_LIST = /^(MON|TUE|WED|THU|FRI|SAT|SUN)(,(MON|TUE|WED|THU|FRI|SAT|SUN))*$/;

function isNum(token: string | undefined): boolean {
  return token !== undefined && NUM.test(token);
}

function isDayList(token: string | undefined): boolean {
  return token !== undefined && DAY_LIST.test(token);
}

/** Builds a Quartz cron string from a structured recurrence pattern (PLAN.md §9.2). */
export function buildCron(pattern: RecurrencePattern): string {
  switch (pattern.type) {
    case "DAILY":
      return `0 ${pattern.minute} ${pattern.hour} * * ?`;
    case "MONTHLY":
      return `0 ${pattern.minute} ${pattern.hour} ${pattern.day} * ?`;
    case "YEARLY":
      return `0 ${pattern.minute} ${pattern.hour} ${pattern.day} ${pattern.month} ?`;
    case "WEEKLY":
      return `0 ${pattern.minute} ${pattern.hour} ? * ${pattern.days.join(",")}`;
    case "INTERVAL": {
      const days = pattern.days.length > 0 ? pattern.days.join(",") : "*";
      if (pattern.intervalUnit === "MINUTE") {
        return `0 ${pattern.startMinute}/${pattern.intervalValue} ${pattern.startHour}-${pattern.endHour} ? * ${days}`;
      }
      return `0 ${pattern.startMinute} ${pattern.startHour}-${pattern.endHour}/${pattern.intervalValue} ? * ${days}`;
    }
    case "ONCE":
      return `0 ${pattern.minute} ${pattern.hour} ${pattern.day} ${pattern.month} ? ${pattern.year}`;
    case "CUSTOM":
      return pattern.cron;
  }
}

/**
 * Reverse-matches a cron string against the 6 generation shapes above (PLAN.md §9.3). Returns
 * null (= CUSTOM/unmatched) rather than a CustomPattern - callers decide how to represent the
 * "advanced mode" fallback, since this function's job is only pattern recognition.
 */
export function parseCron(cron: string): RecurrencePattern | null {
  const fields = cron.trim().split(/\s+/);
  if (fields[0] !== "0") {
    return null;
  }

  // ONCE: 0 mm HH day month ? year (7 fields)
  if (fields.length === 7 && fields[5] === "?" && isNum(fields[1]) && isNum(fields[2]) && isNum(fields[3]) && isNum(fields[4]) && isNum(fields[6])) {
    return {
      type: "ONCE",
      minute: Number(fields[1]),
      hour: Number(fields[2]),
      day: Number(fields[3]),
      month: Number(fields[4]),
      year: Number(fields[6]),
    };
  }

  if (fields.length !== 6) {
    return null;
  }

  // YEARLY: 0 mm HH day month ?
  if (fields[5] === "?" && isNum(fields[1]) && isNum(fields[2]) && isNum(fields[3]) && isNum(fields[4])) {
    return {
      type: "YEARLY",
      minute: Number(fields[1]),
      hour: Number(fields[2]),
      day: Number(fields[3]),
      month: Number(fields[4]),
    };
  }

  // MONTHLY: 0 mm HH day * ?
  if (fields[4] === "*" && fields[5] === "?" && isNum(fields[1]) && isNum(fields[2]) && isNum(fields[3])) {
    return {
      type: "MONTHLY",
      minute: Number(fields[1]),
      hour: Number(fields[2]),
      day: Number(fields[3]),
    };
  }

  // DAILY: 0 mm HH * * ?
  if (fields[3] === "*" && fields[4] === "*" && fields[5] === "?" && isNum(fields[1]) && isNum(fields[2])) {
    return {
      type: "DAILY",
      minute: Number(fields[1]),
      hour: Number(fields[2]),
    };
  }

  // The remaining shapes all have "? *" at positions 3-4.
  if (fields[3] !== "?" || fields[4] !== "*") {
    return null;
  }

  // WEEKLY: 0 mm HH ? * DAYS
  if (isNum(fields[1]) && isNum(fields[2]) && isDayList(fields[5])) {
    return {
      type: "WEEKLY",
      minute: Number(fields[1]),
      hour: Number(fields[2]),
      days: fields[5].split(",") as DayOfWeek[],
    };
  }

  const daysField = fields[5] === "*" || isDayList(fields[5]) ? fields[5] : null;
  if (daysField === null) {
    return null;
  }
  const days = daysField === "*" ? [] : (daysField.split(",") as DayOfWeek[]);

  // INTERVAL, minute-based: 0 startMin/N startHour-endHour ? * DAYS
  const minuteIntervalMatch = fields[1].match(/^(\d{1,2})\/(\d{1,2})$/);
  const hourRangeMatch = fields[2].match(/^(\d{1,2})-(\d{1,2})$/);
  if (minuteIntervalMatch && hourRangeMatch) {
    return {
      type: "INTERVAL",
      intervalUnit: "MINUTE",
      startMinute: Number(minuteIntervalMatch[1]),
      intervalValue: Number(minuteIntervalMatch[2]),
      startHour: Number(hourRangeMatch[1]),
      endHour: Number(hourRangeMatch[2]),
      days,
    };
  }

  // INTERVAL, hour-based: 0 startMin startHour-endHour/N ? * DAYS
  const hourIntervalRangeMatch = fields[2].match(/^(\d{1,2})-(\d{1,2})\/(\d{1,2})$/);
  if (isNum(fields[1]) && hourIntervalRangeMatch) {
    return {
      type: "INTERVAL",
      intervalUnit: "HOUR",
      startMinute: Number(fields[1]),
      startHour: Number(hourIntervalRangeMatch[1]),
      endHour: Number(hourIntervalRangeMatch[2]),
      intervalValue: Number(hourIntervalRangeMatch[3]),
      days,
    };
  }

  return null;
}
