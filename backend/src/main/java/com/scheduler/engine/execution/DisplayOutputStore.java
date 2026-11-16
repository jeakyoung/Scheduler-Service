package com.scheduler.engine.execution;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Latest-snapshot-only in-memory cache (PLAN.md §3.3) - not an execution history.
 * Fine to lose on restart; the next run repopulates it.
 *
 * 2026-11-16 (multi-target-DB round): keyed by (scheduleId, targetDbConfigId) instead of
 * scheduleId alone - one schedule can now have one latest snapshot per target DB, never
 * merged (PLAN.md §3.3).
 */
@Component
public class DisplayOutputStore {

    private record Key(long scheduleId, long targetDbConfigId) {
    }

    private final Map<Key, DisplayOutput> latestByKey = new ConcurrentHashMap<>();

    public void put(DisplayOutput output) {
        latestByKey.put(new Key(output.scheduleId(), output.targetDbConfigId()), output);
    }

    public Optional<DisplayOutput> get(long scheduleId, long targetDbConfigId) {
        return Optional.ofNullable(latestByKey.get(new Key(scheduleId, targetDbConfigId)));
    }

    /** All target DBs' latest snapshots for one schedule (PLAN.md §6 last-result/run-now). */
    public List<DisplayOutput> getAllForSchedule(long scheduleId) {
        return latestByKey.entrySet().stream()
                .filter(entry -> entry.getKey().scheduleId() == scheduleId)
                .map(Map.Entry::getValue)
                .toList();
    }
}
