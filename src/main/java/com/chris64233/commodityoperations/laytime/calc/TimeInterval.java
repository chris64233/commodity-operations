package com.chris64233.commodityoperations.laytime.calc;

import java.time.Instant;

/**
 * 停算区间，记录配对的暂停/复工外部事件号。
 */
public record TimeInterval(Instant from, Instant to, String pauseEventNo, String resumeEventNo) {

    public long durationSeconds() {
        return to.getEpochSecond() - from.getEpochSecond();
    }
}
