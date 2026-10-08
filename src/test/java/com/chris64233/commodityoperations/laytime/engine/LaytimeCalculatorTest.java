package com.chris64233.commodityoperations.laytime.engine;

import com.chris64233.commodityoperations.laytime.domain.CountingBasis;
import com.chris64233.commodityoperations.laytime.domain.EventType;
import com.chris64233.commodityoperations.laytime.domain.OperationEvent;
import com.chris64233.commodityoperations.laytime.domain.Voyage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LaytimeCalculatorTest {

    private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");
    private final TimelineNormalizer normalizer = new TimelineNormalizer();
    private final LaytimeCalculator calculator = new LaytimeCalculator();

    private Voyage voyage(CountingBasis basis, long allowedSeconds, String rate) {
        return new Voyage("V1", "MV TEST", Duration.ofSeconds(allowedSeconds),
                new BigDecimal(rate), "USD", basis);
    }

    private NormalizedTimeline standardTimeline() {
        List<OperationEvent> events = List.of(
                new OperationEvent("v", "berth", EventType.BERTH, T0, T0),
                new OperationEvent("v", "start", EventType.START, T0.plus(Duration.ofHours(6)), T0),
                new OperationEvent("v", "s1", EventType.SUSPEND, T0.plus(Duration.ofHours(10)), T0),
                new OperationEvent("v", "r1", EventType.RESUME, T0.plus(Duration.ofHours(14)), T0),
                new OperationEvent("v", "done", EventType.COMPLETE, T0.plus(Duration.ofHours(30)), T0));
        return normalizer.normalize(events);
    }

    @Test
    void onBerthBasisCountsFromBerthAndExcludesSuspension() {
        // 起算 00:00，完工 30:00，暂停 4 小时 → 已用 26 小时
        LaytimeResult result = calculator.calculate(
                voyage(CountingBasis.ON_BERTH, Duration.ofHours(24).getSeconds(), "12000"),
                standardTimeline());
        assertEquals(Duration.ofHours(26), result.usedLaytime());
        assertEquals(Duration.ofHours(4), result.suspended());
        assertEquals(Duration.ofHours(2), result.excess());
        // 2/24 * 12000 = 1000.00
        assertEquals(new BigDecimal("1000.00"), result.demurrage());
    }

    @Test
    void onWorkBasisCountsFromStart() {
        // 起算 06:00，完工 30:00（24 小时跨度），暂停 4 小时 → 已用 20 小时，无滞期
        LaytimeResult result = calculator.calculate(
                voyage(CountingBasis.ON_WORK, Duration.ofHours(20).getSeconds(), "12000"),
                standardTimeline());
        assertEquals(Duration.ofHours(20), result.usedLaytime());
        assertEquals(Duration.ZERO, result.excess());
        assertEquals(BigDecimal.ZERO.setScale(2), result.demurrage());
    }

    @Test
    void noDemurrageWhenWithinAllowance() {
        LaytimeResult result = calculator.calculate(
                voyage(CountingBasis.ON_BERTH, Duration.ofHours(48).getSeconds(), "12000"),
                standardTimeline());
        assertEquals(Duration.ZERO, result.excess());
        assertEquals(0, result.demurrage().compareTo(BigDecimal.ZERO));
    }

    @Test
    void fractionalDayRoundsToCents() {
        // 超 1 小时：1/24 * 24000 = 1000.00；超 30 分钟 = 500.00；超 1 秒 ≈ 0.28
        NormalizedTimeline timeline = normalizer.normalize(List.of(
                new OperationEvent("v", "berth", EventType.BERTH, T0, T0),
                new OperationEvent("v", "start", EventType.START, T0, T0),
                new OperationEvent("v", "done", EventType.COMPLETE,
                        T0.plus(Duration.ofHours(24)).plusSeconds(1), T0)));
        LaytimeResult result = calculator.calculate(
                voyage(CountingBasis.ON_WORK, Duration.ofHours(24).getSeconds(), "24000"), timeline);
        assertEquals(Duration.ofSeconds(1), result.excess());
        assertEquals(new BigDecimal("0.28"), result.demurrage());
    }
}
