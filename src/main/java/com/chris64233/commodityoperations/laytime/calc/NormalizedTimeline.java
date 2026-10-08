package com.chris64233.commodityoperations.laytime.calc;

import java.time.Instant;
import java.util.List;

/**
 * 规范化结果：有序时间线、去重合并后的停算区间、关键锚点。
 */
public record NormalizedTimeline(
        List<RawEvent> orderedEvents,
        List<TimeInterval> suspensions,
        Instant berthAt,
        Instant startAt,
        Instant completeAt,
        long grossWorkSeconds,
        long deductedSeconds,
        long usedSeconds) {
}
