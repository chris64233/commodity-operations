package com.chris64233.commodityoperations.laytime.engine;

import java.time.Duration;
import java.time.Instant;

/**
 * 规范化后的停算区间（重叠暂停已合并，只扣一次）。
 */
public record SuspensionInterval(Instant from, Instant to) {

    public Duration duration() {
        return Duration.between(from, to);
    }
}
