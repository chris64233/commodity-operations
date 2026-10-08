package com.chris64233.commodityoperations.laytime.engine;

import com.chris64233.commodityoperations.laytime.domain.EventType;
import com.chris64233.commodityoperations.laytime.domain.OperationEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 按发生顺序重建并校验后的作业时间线。
 *
 * @param berthAt        靠泊时间（ON_BERTH 口径的起算点）
 * @param startAt        开工时间（ON_WORK 口径的起算点）
 * @param completeAt     完工时间
 * @param suspensions    合并去重后的停算区间
 * @param orderedEvents  按发生时间排序的事件
 */
public record NormalizedTimeline(Instant berthAt,
                                 Instant startAt,
                                 Instant completeAt,
                                 List<SuspensionInterval> suspensions,
                                 List<OperationEvent> orderedEvents) {

    public Duration totalSuspended() {
        return suspensions.stream().map(SuspensionInterval::duration).reduce(Duration.ZERO, Duration::plus);
    }

    public Instant countingStart(com.chris64233.commodityoperations.laytime.domain.CountingBasis basis) {
        return basis == com.chris64233.commodityoperations.laytime.domain.CountingBasis.ON_BERTH ? berthAt : startAt;
    }

    public OperationEvent eventOfType(EventType type) {
        return orderedEvents.stream().filter(e -> e.getType() == type).findFirst().orElse(null);
    }
}
