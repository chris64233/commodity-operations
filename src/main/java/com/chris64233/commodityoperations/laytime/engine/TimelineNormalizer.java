package com.chris64233.commodityoperations.laytime.engine;

import com.chris64233.commodityoperations.laytime.domain.EventType;
import com.chris64233.commodityoperations.laytime.domain.OperationEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * 按事件发生顺序重建作业时间线。
 *
 * <p>合法事件流要求：唯一的 BERTH → 唯一的 START → 任意配对的 SUSPEND/RESUME →
 * 唯一的 COMPLETE；各关键事件的发生时间必须严格递增；SUSPEND 必须有匹配的 RESUME，
 * RESUME 不能没有前置 SUSPEND；同一时刻允许不同关键事件（零耗时），但暂停必须有正长度。</p>
 *
 * <p>暂停允许重叠（例如两个不同部门同时挂起作业）：多个 SUSPEND 入栈，RESUME 与
 * 最早未闭合的 SUSPEND 配对，随后对区间做并集合并，保证重叠部分只扣除一次。</p>
 */
@Component
public class TimelineNormalizer {

    public NormalizedTimeline normalize(List<OperationEvent> events) {
        List<String> problems = new ArrayList<>();

        List<OperationEvent> ordered = events.stream()
                .sorted(Comparator.comparing(OperationEvent::getOccurredAt)
                        .thenComparing(OperationEvent::getReceivedAt)
                        .thenComparing(e -> e.getType().name()))
                .toList();

        OperationEvent berth = null;
        OperationEvent start = null;
        OperationEvent complete = null;
        for (OperationEvent e : ordered) {
            switch (e.getType()) {
                case BERTH -> {
                    if (berth != null) {
                        problems.add(duplicate(EventType.BERTH, berth, e));
                    }
                    berth = e;
                }
                case START -> {
                    if (start != null) {
                        problems.add(duplicate(EventType.START, start, e));
                    }
                    start = e;
                }
                case COMPLETE -> {
                    if (complete != null) {
                        problems.add(duplicate(EventType.COMPLETE, complete, e));
                    }
                    complete = e;
                }
                default -> {
                }
            }
        }

        if (berth == null) {
            problems.add("缺少必要的前置事件 BERTH（靠泊）");
        }
        if (start == null) {
            problems.add("缺少必要的前置事件 START（开工）");
        }
        if (complete == null) {
            problems.add("缺少 COMPLETE（完工）事件，无法结算");
        }

        if (!problems.isEmpty()) {
            throw new TimelineException(problems);
        }

        if (start.getOccurredAt().isBefore(berth.getOccurredAt())) {
            problems.add(String.format("时间线顺序错误：开工 %s 早于靠泊 %s", start.getOccurredAt(), berth.getOccurredAt()));
        }
        if (complete.getOccurredAt().isBefore(start.getOccurredAt())) {
            problems.add(String.format("结束早于开始：完工 %s 早于开工 %s", complete.getOccurredAt(), start.getOccurredAt()));
        }

        List<SuspensionInterval> suspensions = extractSuspensions(ordered, berth.getOccurredAt(),
                complete.getOccurredAt(), problems);

        if (!problems.isEmpty()) {
            throw new TimelineException(problems);
        }
        return new NormalizedTimeline(berth.getOccurredAt(), start.getOccurredAt(),
                complete.getOccurredAt(), suspensions, ordered);
    }

    private List<SuspensionInterval> extractSuspensions(List<OperationEvent> ordered, Instant berthAt,
                                                        Instant completeAt, List<String> problems) {
        LinkedList<Instant> openSuspends = new LinkedList<>();
        List<SuspensionInterval> raw = new ArrayList<>();

        for (OperationEvent e : ordered) {
            if (e.getType() == EventType.SUSPEND) {
                openSuspends.addLast(e.getOccurredAt());
            } else if (e.getType() == EventType.RESUME) {
                if (openSuspends.isEmpty()) {
                    problems.add(String.format("无法解释的时间线：RESUME（复工）事件 %s 没有匹配的 SUSPEND（暂停）",
                            e.getExternalEventNo()));
                    continue;
                }
                Instant suspendAt = openSuspends.pollFirst();
                Instant resumeAt = e.getOccurredAt();
                if (resumeAt.isBefore(suspendAt)) {
                    problems.add(String.format("结束早于开始：复工 %s 早于暂停 %s（事件 %s）",
                            resumeAt, suspendAt, e.getExternalEventNo()));
                } else if (resumeAt.equals(suspendAt)) {
                    problems.add(String.format("停算区间长度为 0：暂停与复工同为 %s（事件 %s）",
                            suspendAt, e.getExternalEventNo()));
                } else {
                    raw.add(new SuspensionInterval(suspendAt, resumeAt));
                }
            }
        }

        if (!openSuspends.isEmpty()) {
            for (Instant at : openSuspends) {
                problems.add(String.format("无法解释的时间线：%s 的 SUSPEND（暂停）没有匹配的 RESUME（复工）", at));
            }
        }

        for (SuspensionInterval s : raw) {
            if (s.from().isBefore(berthAt)) {
                problems.add(String.format("暂停区间 %s~%s 早于靠泊 %s", s.from(), s.to(), berthAt));
            }
            if (s.to().isAfter(completeAt)) {
                problems.add(String.format("暂停区间 %s~%s 晚于完工 %s", s.from(), s.to(), completeAt));
            }
        }

        return merge(raw);
    }

    /** 区间按起点排序后做并集，重叠/相邻区间只计一次。 */
    private List<SuspensionInterval> merge(List<SuspensionInterval> raw) {
        List<SuspensionInterval> sorted = raw.stream()
                .sorted(Comparator.comparing(SuspensionInterval::from)).toList();
        List<SuspensionInterval> merged = new ArrayList<>();
        for (SuspensionInterval s : sorted) {
            if (merged.isEmpty()) {
                merged.add(s);
                continue;
            }
            SuspensionInterval last = merged.get(merged.size() - 1);
            if (!s.from().isAfter(last.to())) {
                Instant to = s.to().isAfter(last.to()) ? s.to() : last.to();
                merged.set(merged.size() - 1, new SuspensionInterval(last.from(), to));
            } else {
                merged.add(s);
            }
        }
        return merged;
    }

    private String duplicate(EventType type, OperationEvent first, OperationEvent again) {
        return String.format("无法解释的时间线：存在重复的 %s 事件（%s 与 %s）",
                type, first.getExternalEventNo(), again.getExternalEventNo());
    }
}
