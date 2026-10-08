package com.chris64233.commodityoperations.laytime.engine;

import com.chris64233.commodityoperations.laytime.domain.EventType;
import com.chris64233.commodityoperations.laytime.domain.OperationEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TimelineNormalizerTest {

    private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");
    private final TimelineNormalizer normalizer = new TimelineNormalizer();

    private OperationEvent ev(String no, EventType type, Instant at) {
        return new OperationEvent("voyage", no, type, at, at.plusSeconds(1));
    }

    @Test
    void buildsTimelineAndPairsSuspensions() {
        NormalizedTimeline timeline = normalizer.normalize(List.of(
                ev("c", EventType.COMPLETE, T0.plus(Duration.ofHours(20))),
                ev("b", EventType.START, T0.plus(Duration.ofHours(2))),
                ev("a", EventType.BERTH, T0),
                ev("s1", EventType.SUSPEND, T0.plus(Duration.ofHours(4))),
                ev("r1", EventType.RESUME, T0.plus(Duration.ofHours(8)))));

        assertEquals(T0, timeline.berthAt());
        assertEquals(T0.plus(Duration.ofHours(2)), timeline.startAt());
        assertEquals(T0.plus(Duration.ofHours(20)), timeline.completeAt());
        assertEquals(1, timeline.suspensions().size());
        assertEquals(Duration.ofHours(4), timeline.totalSuspended());
    }

    @Test
    void overlappingSuspensionsAreDeduplicated() {
        NormalizedTimeline timeline = normalizer.normalize(List.of(
                ev("a", EventType.BERTH, T0),
                ev("b", EventType.START, T0.plus(Duration.ofHours(1))),
                ev("s1", EventType.SUSPEND, T0.plus(Duration.ofHours(2))),
                ev("s2", EventType.SUSPEND, T0.plus(Duration.ofHours(3))),
                ev("r1", EventType.RESUME, T0.plus(Duration.ofHours(6))),
                ev("r2", EventType.RESUME, T0.plus(Duration.ofHours(8))),
                ev("c", EventType.COMPLETE, T0.plus(Duration.ofHours(20)))));

        // [2,6] 与 [3,8] 重叠 → 合并为 [2,8]，共 6 小时，而不是 4+5=9 小时
        assertEquals(1, timeline.suspensions().size());
        assertEquals(Duration.ofHours(6), timeline.totalSuspended());
    }

    @Test
    void rejectsMissingPrerequisites() {
        TimelineException ex = assertThrows(TimelineException.class,
                () -> normalizer.normalize(List.of(
                        ev("a", EventType.BERTH, T0),
                        ev("c", EventType.COMPLETE, T0.plus(Duration.ofHours(1))))));
        assertTrue(ex.getProblems().stream().anyMatch(p -> p.contains("START")));
    }

    @Test
    void rejectsCompleteBeforeStart() {
        TimelineException ex = assertThrows(TimelineException.class,
                () -> normalizer.normalize(List.of(
                        ev("a", EventType.BERTH, T0),
                        ev("b", EventType.START, T0.plus(Duration.ofHours(10))),
                        ev("c", EventType.COMPLETE, T0.plus(Duration.ofHours(5))))));
        assertTrue(ex.getMessage().contains("结束早于开始"));
    }

    @Test
    void rejectsResumeWithoutSuspend() {
        TimelineException ex = assertThrows(TimelineException.class,
                () -> normalizer.normalize(List.of(
                        ev("a", EventType.BERTH, T0),
                        ev("b", EventType.START, T0.plus(Duration.ofHours(1))),
                        ev("r1", EventType.RESUME, T0.plus(Duration.ofHours(2))),
                        ev("c", EventType.COMPLETE, T0.plus(Duration.ofHours(5))))));
        assertTrue(ex.getMessage().contains("没有匹配的 SUSPEND"));
    }

    @Test
    void rejectsUnclosedSuspend() {
        TimelineException ex = assertThrows(TimelineException.class,
                () -> normalizer.normalize(List.of(
                        ev("a", EventType.BERTH, T0),
                        ev("b", EventType.START, T0.plus(Duration.ofHours(1))),
                        ev("s1", EventType.SUSPEND, T0.plus(Duration.ofHours(2))),
                        ev("c", EventType.COMPLETE, T0.plus(Duration.ofHours(5))))));
        assertTrue(ex.getMessage().contains("没有匹配的 RESUME"));
    }

    @Test
    void rejectsZeroLengthSuspensionAndDuplicateKeystone() {
        assertThrows(TimelineException.class, () -> normalizer.normalize(List.of(
                ev("a", EventType.BERTH, T0),
                ev("b", EventType.START, T0.plus(Duration.ofHours(1))),
                ev("s1", EventType.SUSPEND, T0.plus(Duration.ofHours(2))),
                ev("r1", EventType.RESUME, T0.plus(Duration.ofHours(2))),
                ev("c", EventType.COMPLETE, T0.plus(Duration.ofHours(5))))));

        TimelineException dup = assertThrows(TimelineException.class,
                () -> normalizer.normalize(List.of(
                        ev("a1", EventType.BERTH, T0),
                        ev("a2", EventType.BERTH, T0),
                        ev("b", EventType.START, T0.plus(Duration.ofHours(1))),
                        ev("c", EventType.COMPLETE, T0.plus(Duration.ofHours(5))))));
        assertTrue(dup.getMessage().contains("重复"));
    }
}
