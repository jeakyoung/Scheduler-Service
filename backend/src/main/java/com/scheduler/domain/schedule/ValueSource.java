package com.scheduler.domain.schedule;

/**
 * How a schedule_parameter's bound value is resolved at execution time (SpParameterResolver,
 * engine/sp - T6). New sources are added as enum values only, no schema change (PLAN.md §4.2).
 */
public enum ValueSource {
    LITERAL,
    CURRENT_DATE,
    CURRENT_DATETIME
}
