package com.scheduler.engine.execution;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Latest-snapshot-only in-memory cache, keyed by scheduleId (PLAN.md §3.3) - not an
 * execution history. Fine to lose on restart; the next run repopulates it.
 */
@Component
public class DisplayOutputStore {

    private final Map<Long, DisplayOutput> latestByScheduleId = new ConcurrentHashMap<>();

    public void put(DisplayOutput output) {
        latestByScheduleId.put(output.scheduleId(), output);
    }

    public Optional<DisplayOutput> get(long scheduleId) {
        return Optional.ofNullable(latestByScheduleId.get(scheduleId));
    }
}
