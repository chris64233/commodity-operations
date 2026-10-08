package com.chris64233.commodityoperations.laytime.calc;

import com.chris64233.commodityoperations.laytime.domain.EventType;

import java.time.Instant;

/**
 * 参与时间线重建的事件输入。
 */
public record RawEvent(String externalEventNo, EventType type, Instant occurredAt) {
}
