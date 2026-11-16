package com.scheduler.web.api.dto;

/**
 * Resolved {id, name} pair for a schedule's target DBs (PLAN.md §6/§7 - lets ScheduleList
 * render name badges without an N+1 lookup per schedule).
 */
public record TargetDbConfigRef(long id, String name) {
}
