import { describe, expect, it } from "vitest";
import { buildCron, parseCron, type RecurrencePattern } from "./cronPatterns";

describe("buildCron", () => {
  it("DAILY", () => {
    expect(buildCron({ type: "DAILY", hour: 9, minute: 30 })).toBe("0 30 9 * * ?");
  });

  it("MONTHLY", () => {
    expect(buildCron({ type: "MONTHLY", day: 15, hour: 8, minute: 0 })).toBe("0 0 8 15 * ?");
  });

  it("YEARLY", () => {
    expect(buildCron({ type: "YEARLY", month: 12, day: 25, hour: 9, minute: 0 })).toBe("0 0 9 25 12 ?");
  });

  it("WEEKLY", () => {
    expect(buildCron({ type: "WEEKLY", days: ["MON", "WED", "FRI"], hour: 9, minute: 0 })).toBe(
      "0 0 9 ? * MON,WED,FRI",
    );
  });

  it("INTERVAL minute-based", () => {
    expect(
      buildCron({
        type: "INTERVAL",
        startHour: 9,
        startMinute: 0,
        endHour: 18,
        intervalValue: 30,
        intervalUnit: "MINUTE",
        days: ["MON", "TUE", "WED", "THU", "FRI"],
      }),
    ).toBe("0 0/30 9-18 ? * MON,TUE,WED,THU,FRI");
  });

  it("INTERVAL hour-based", () => {
    expect(
      buildCron({
        type: "INTERVAL",
        startHour: 0,
        startMinute: 0,
        endHour: 23,
        intervalValue: 2,
        intervalUnit: "HOUR",
        days: [],
      }),
    ).toBe("0 0 0-23/2 ? * *");
  });

  it("ONCE", () => {
    expect(buildCron({ type: "ONCE", year: 2027, month: 1, day: 1, hour: 0, minute: 0 })).toBe("0 0 0 1 1 ? 2027");
  });

  it("CUSTOM passes the raw string through unchanged", () => {
    expect(buildCron({ type: "CUSTOM", cron: "0 0 12 1/2 * ?" })).toBe("0 0 12 1/2 * ?");
  });
});

describe("parseCron round trip", () => {
  const cases: RecurrencePattern[] = [
    { type: "DAILY", hour: 9, minute: 30 },
    { type: "MONTHLY", day: 1, hour: 8, minute: 0 },
    { type: "MONTHLY", day: 31, hour: 23, minute: 59 },
    { type: "YEARLY", month: 6, day: 15, hour: 12, minute: 0 },
    { type: "WEEKLY", days: ["MON", "WED", "FRI"], hour: 9, minute: 0 },
    { type: "WEEKLY", days: ["SAT", "SUN"], hour: 10, minute: 15 },
    {
      type: "INTERVAL",
      startHour: 9,
      startMinute: 0,
      endHour: 18,
      intervalValue: 30,
      intervalUnit: "MINUTE",
      days: ["MON", "TUE", "WED", "THU", "FRI"],
    },
    // Boundary case flagged in PLAN.md §9.2: full-day interval with no day filter (every day).
    { type: "INTERVAL", startHour: 0, startMinute: 0, endHour: 23, intervalValue: 1, intervalUnit: "HOUR", days: [] },
    { type: "ONCE", year: 2027, month: 3, day: 10, hour: 14, minute: 45 },
  ];

  it.each(cases)("round-trips $type", (pattern) => {
    const cron = buildCron(pattern);
    expect(parseCron(cron)).toEqual(pattern);
  });

  it("returns null for a cron that doesn't match any of the 6 patterns", () => {
    // day-of-month AND day-of-week both specific is invalid Quartz anyway, but more to the
    // point here, it's not one of the 6 generated shapes.
    expect(parseCron("0 0 12 1/2 * ?")).toBeNull();
  });

  it("returns null for an arbitrary hand-written cron (legacy import style)", () => {
    expect(parseCron("0 15 10 ? * 6#3")).toBeNull();
  });

  it("returns null for garbage input", () => {
    expect(parseCron("not a cron")).toBeNull();
  });
});
