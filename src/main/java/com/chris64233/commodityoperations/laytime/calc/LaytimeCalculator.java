package com.chris64233.commodityoperations.laytime.calc;

import com.chris64233.commodityoperations.laytime.domain.EventType;
import com.chris64233.commodityoperations.laytime.domain.StopRule;
import com.chris64233.commodityoperations.laytime.exception.LaytimeValidationException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 按事件发生顺序重建作业时间线并计算有效占用时间。
 *
 * 口径：装卸时间自“开工”起算至“完工”止；开工之前（含靠泊等待）不计。
 * 暂停区间从有效时间中扣除，相互重叠或嵌套的暂停只扣除一次（按并集）。
 */
public final class LaytimeCalculator {

    private LaytimeCalculator() {
    }

    public static NormalizedTimeline normalize(List<RawEvent> events, StopRule stopRule) {
        if (events == null || events.isEmpty()) {
            throw new LaytimeValidationException("缺少作业事件，无法重建时间线");
        }

        List<RawEvent> ordered = events.stream()
                .sorted(Comparator
                        .comparing(RawEvent::occurredAt)
                        .thenComparing(e -> e.type().getOrder())
                        .thenComparing(RawEvent::externalEventNo))
                .toList();

        RawEvent berth = requireSingle(ordered, EventType.BERTH);
        RawEvent start = requireSingle(ordered, EventType.START);
        RawEvent complete = requireSingle(ordered, EventType.COMPLETE);

        if (start.occurredAt().isBefore(berth.occurredAt())) {
            throw new LaytimeValidationException(
                    "开工时间 " + start.occurredAt() + " 早于靠泊时间 " + berth.occurredAt());
        }
        if (complete.occurredAt().isBefore(start.occurredAt())) {
            throw new LaytimeValidationException(
                    "完工时间 " + complete.occurredAt() + " 早于开工时间 " + start.occurredAt());
        }

        List<TimeInterval> rawSuspensions = pairSuspensions(ordered, start, complete);
        List<TimeInterval> merged = stopRule == StopRule.DEDUCT_PAUSE_INTERVALS
                ? mergeIntervals(rawSuspensions)
                : List.of();

        long gross = complete.occurredAt().getEpochSecond() - start.occurredAt().getEpochSecond();
        long deducted = merged.stream().mapToLong(TimeInterval::durationSeconds).sum();
        long used = gross - deducted;

        return new NormalizedTimeline(ordered, merged,
                berth.occurredAt(), start.occurredAt(), complete.occurredAt(),
                gross, deducted, used);
    }

    private static RawEvent requireSingle(List<RawEvent> ordered, EventType type) {
        List<RawEvent> found = ordered.stream().filter(e -> e.type() == type).toList();
        if (found.isEmpty()) {
            throw new LaytimeValidationException("缺少必要的前置事件：" + type.getLabel());
        }
        if (found.size() > 1) {
            throw new LaytimeValidationException(
                    type.getLabel() + " 事件存在多个（外部事件号："
                            + found.stream().map(RawEvent::externalEventNo).toList() + "），时间线无法解释");
        }
        return found.get(0);
    }

    private static List<TimeInterval> pairSuspensions(List<RawEvent> ordered,
                                                       RawEvent start, RawEvent complete) {
        List<TimeInterval> intervals = new ArrayList<>();
        List<RawEvent> openPauses = new ArrayList<>();
        for (RawEvent event : ordered) {
            switch (event.type()) {
                case PAUSE -> {
                    if (event.occurredAt().isBefore(start.occurredAt())) {
                        throw new LaytimeValidationException(
                                "暂停事件 " + event.externalEventNo() + " 发生于开工之前，时间线无法解释");
                    }
                    if (event.occurredAt().isAfter(complete.occurredAt())) {
                        throw new LaytimeValidationException(
                                "暂停事件 " + event.externalEventNo() + " 发生于完工之后，时间线无法解释");
                    }
                    openPauses.add(event);
                }
                case RESUME -> {
                    if (event.occurredAt().isBefore(start.occurredAt())) {
                        throw new LaytimeValidationException(
                                "复工事件 " + event.externalEventNo() + " 发生于开工之前，时间线无法解释");
                    }
                    if (event.occurredAt().isAfter(complete.occurredAt())) {
                        throw new LaytimeValidationException(
                                "复工事件 " + event.externalEventNo() + " 发生于完工之后，时间线无法解释");
                    }
                    if (openPauses.isEmpty()) {
                        throw new LaytimeValidationException(
                                "复工事件 " + event.externalEventNo() + " 缺少对应的暂停事件");
                    }
                    RawEvent pause = openPauses.remove(0);
                    if (event.occurredAt().isBefore(pause.occurredAt())) {
                        throw new LaytimeValidationException(
                                "复工事件 " + event.externalEventNo() + " 早于其暂停事件 "
                                        + pause.externalEventNo() + " 的发生时间");
                    }
                    intervals.add(new TimeInterval(
                            pause.occurredAt(), event.occurredAt(),
                            pause.externalEventNo(), event.externalEventNo()));
                }
                default -> {
                    // BERTH / START / COMPLETE 已在锚点校验中处理
                }
            }
        }
        if (!openPauses.isEmpty()) {
            throw new LaytimeValidationException(
                    "暂停事件 " + openPauses.stream().map(RawEvent::externalEventNo).toList()
                            + " 在完工时仍未复工，时间线不完整");
        }
        return intervals;
    }

    /** 合并重叠/相邻区间，保证重叠暂停只扣除一次。 */
    static List<TimeInterval> mergeIntervals(List<TimeInterval> intervals) {
        if (intervals.isEmpty()) {
            return List.of();
        }
        List<TimeInterval> sorted = intervals.stream()
                .sorted(Comparator.comparing(TimeInterval::from).thenComparing(TimeInterval::to))
                .toList();
        List<TimeInterval> merged = new ArrayList<>();
        Instant from = sorted.get(0).from();
        Instant to = sorted.get(0).to();
        Set<String> pauseRefs = new HashSet<>();
        pauseRefs.add(sorted.get(0).pauseEventNo());
        for (int i = 1; i < sorted.size(); i++) {
            TimeInterval current = sorted.get(i);
            if (!current.from().isAfter(to)) {
                if (current.to().isAfter(to)) {
                    to = current.to();
                }
                pauseRefs.add(current.pauseEventNo());
            } else {
                merged.add(new TimeInterval(from, to, String.join(",", pauseRefs), null));
                from = current.from();
                to = current.to();
                pauseRefs = new HashSet<>();
                pauseRefs.add(current.pauseEventNo());
            }
        }
        merged.add(new TimeInterval(from, to, String.join(",", pauseRefs), null));
        return merged;
    }
}
