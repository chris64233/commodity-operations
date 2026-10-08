package com.chris64233.commodityoperations.laytime.calc;

import com.chris64233.commodityoperations.laytime.domain.EventType;
import com.chris64233.commodityoperations.laytime.domain.StopRule;
import com.chris64233.commodityoperations.laytime.exception.LaytimeValidationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LaytimeCalculatorTest {

    private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");

    private static RawEvent event(String no, EventType type, long hoursAfterStart) {
        return new RawEvent(no, type, T0.plusSeconds((long) hoursAfterStart * 3600));
    }

    @Test
    void 开工到完工扣除暂停区间计算有效时间() {
        // 08 靠泊，10 开工，12-13 暂停，18 完工
        List<RawEvent> events = List.of(
                event("e1", EventType.BERTH, 8),
                event("e2", EventType.START, 10),
                event("e3", EventType.PAUSE, 12),
                event("e4", EventType.RESUME, 13),
                event("e5", EventType.COMPLETE, 18));

        NormalizedTimeline timeline =
                LaytimeCalculator.normalize(events, StopRule.DEDUCT_PAUSE_INTERVALS);

        assertThat(timeline.grossWorkSeconds()).isEqualTo(8 * 3600L);
        assertThat(timeline.deductedSeconds()).isEqualTo(3600L);
        assertThat(timeline.usedSeconds()).isEqualTo(7 * 3600L);
        assertThat(timeline.suspensions()).hasSize(1);
    }

    @Test
    void 重叠暂停只扣除一次() {
        // 12:00 P1, 12:30 P2, 13:00 R1（配对 P1）, 14:00 R2（配对 P2）
        List<RawEvent> events = List.of(
                event("e1", EventType.BERTH, 8),
                event("e2", EventType.START, 10),
                event("e3", EventType.PAUSE, 12),
                new RawEvent("e4", EventType.PAUSE, T0.plusSeconds(12 * 3600L + 1800)),
                event("e5", EventType.RESUME, 13),
                event("e6", EventType.RESUME, 14),
                event("e7", EventType.COMPLETE, 18));

        NormalizedTimeline timeline =
                LaytimeCalculator.normalize(events, StopRule.DEDUCT_PAUSE_INTERVALS);

        assertThat(timeline.suspensions()).hasSize(1);
        assertThat(timeline.suspensions().get(0).durationSeconds()).isEqualTo(2 * 3600L);
        assertThat(timeline.deductedSeconds()).isEqualTo(2 * 3600L);
        assertThat(timeline.usedSeconds()).isEqualTo(6 * 3600L);
    }

    @Test
    void 事件晚到乱序也能按发生时间重建() {
        List<RawEvent> shuffled = List.of(
                event("e5", EventType.COMPLETE, 18),
                event("e1", EventType.BERTH, 8),
                event("e4", EventType.RESUME, 13),
                event("e2", EventType.START, 10),
                event("e3", EventType.PAUSE, 12));

        NormalizedTimeline timeline =
                LaytimeCalculator.normalize(shuffled, StopRule.DEDUCT_PAUSE_INTERVALS);

        assertThat(timeline.orderedEvents().get(0).externalEventNo()).isEqualTo("e1");
        assertThat(timeline.orderedEvents().get(4).externalEventNo()).isEqualTo("e5");
        assertThat(timeline.usedSeconds()).isEqualTo(7 * 3600L);
    }

    @Test
    void 连续计算规则不扣除暂停() {
        List<RawEvent> events = List.of(
                event("e1", EventType.BERTH, 8),
                event("e2", EventType.START, 10),
                event("e3", EventType.PAUSE, 12),
                event("e4", EventType.RESUME, 13),
                event("e5", EventType.COMPLETE, 18));

        NormalizedTimeline timeline =
                LaytimeCalculator.normalize(events, StopRule.CONTINUOUS);

        assertThat(timeline.suspensions()).isEmpty();
        assertThat(timeline.usedSeconds()).isEqualTo(8 * 3600L);
    }

    @Test
    void 缺少完工事件被拒绝() {
        List<RawEvent> events = List.of(
                event("e1", EventType.BERTH, 8),
                event("e2", EventType.START, 10),
                event("e3", EventType.PAUSE, 12),
                event("e4", EventType.RESUME, 13));

        assertThatThrownBy(() ->
                LaytimeCalculator.normalize(events, StopRule.DEDUCT_PAUSE_INTERVALS))
                .isInstanceOf(LaytimeValidationException.class)
                .hasMessageContaining("完工");
    }

    @Test
    void 完工早于开工被拒绝() {
        List<RawEvent> events = List.of(
                event("e1", EventType.BERTH, 8),
                event("e2", EventType.START, 18),
                event("e5", EventType.COMPLETE, 10));

        assertThatThrownBy(() ->
                LaytimeCalculator.normalize(events, StopRule.DEDUCT_PAUSE_INTERVALS))
                .isInstanceOf(LaytimeValidationException.class)
                .hasMessageContaining("完工时间")
                .hasMessageContaining("早于开工时间");
    }

    @Test
    void 复工缺少暂停配对被拒绝() {
        List<RawEvent> events = List.of(
                event("e1", EventType.BERTH, 8),
                event("e2", EventType.START, 10),
                event("e4", EventType.RESUME, 13),
                event("e5", EventType.COMPLETE, 18));

        assertThatThrownBy(() ->
                LaytimeCalculator.normalize(events, StopRule.DEDUCT_PAUSE_INTERVALS))
                .isInstanceOf(LaytimeValidationException.class)
                .hasMessageContaining("缺少对应的暂停事件");
    }

    @Test
    void 暂停未复工被拒绝() {
        List<RawEvent> events = List.of(
                event("e1", EventType.BERTH, 8),
                event("e2", EventType.START, 10),
                event("e3", EventType.PAUSE, 12),
                event("e5", EventType.COMPLETE, 18));

        assertThatThrownBy(() ->
                LaytimeCalculator.normalize(events, StopRule.DEDUCT_PAUSE_INTERVALS))
                .isInstanceOf(LaytimeValidationException.class)
                .hasMessageContaining("仍未复工");
    }

    @Test
    void 同一锚点事件出现多个被拒绝() {
        List<RawEvent> events = List.of(
                event("e1", EventType.BERTH, 8),
                event("e2", EventType.START, 10),
                event("x", EventType.START, 11),
                event("e5", EventType.COMPLETE, 18));

        assertThatThrownBy(() ->
                LaytimeCalculator.normalize(events, StopRule.DEDUCT_PAUSE_INTERVALS))
                .isInstanceOf(LaytimeValidationException.class)
                .hasMessageContaining("开工")
                .hasMessageContaining("多个");
    }
}
