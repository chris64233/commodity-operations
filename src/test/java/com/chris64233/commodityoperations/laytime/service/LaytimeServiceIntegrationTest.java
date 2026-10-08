package com.chris64233.commodityoperations.laytime.service;

import com.chris64233.commodityoperations.laytime.domain.CountingBasis;
import com.chris64233.commodityoperations.laytime.domain.EventType;
import com.chris64233.commodityoperations.laytime.domain.Settlement;
import com.chris64233.commodityoperations.laytime.domain.SettlementStatus;
import com.chris64233.commodityoperations.laytime.dto.Requests.CreateVoyageRequest;
import com.chris64233.commodityoperations.laytime.dto.Requests.IngestEventRequest;
import com.chris64233.commodityoperations.laytime.dto.Responses.EventResponse;
import com.chris64233.commodityoperations.laytime.dto.Responses.SettlementResponse;
import com.chris64233.commodityoperations.laytime.dto.Responses.VoyageDetailResponse;
import com.chris64233.commodityoperations.laytime.engine.TimelineException;
import com.chris64233.commodityoperations.laytime.repo.SettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LaytimeServiceIntegrationTest {

    @Autowired
    private LaytimeService service;
    @Autowired
    private SettlementRepository settlementRepository;

    private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        settlementRepository.deleteAll();
    }

    private String createVoyage(String code) {
        return service.createVoyage(new CreateVoyageRequest(code, "MV TEST",
                Duration.ofHours(24).toSeconds(), new BigDecimal("12000"),
                "USD", CountingBasis.ON_BERTH)).id();
    }

    private void ingest(String voyage, String no, EventType type, Instant at, Instant received) {
        service.ingestEvent(voyage, new IngestEventRequest(no, type, at, received));
    }

    private void completeBasicVoyage(String voyage, Instant completeAt, Instant receivedAt) {
        ingest(voyage, "berth", EventType.BERTH, T0, receivedAt == null ? T0 : receivedAt);
        ingest(voyage, "start", EventType.START, T0.plus(Duration.ofHours(2)), T0);
        ingest(voyage, "s1", EventType.SUSPEND, T0.plus(Duration.ofHours(8)), T0);
        ingest(voyage, "r1", EventType.RESUME, T0.plus(Duration.ofHours(12)), T0);
        ingest(voyage, "done", EventType.COMPLETE, completeAt,
                receivedAt == null ? completeAt : receivedAt);
    }

    @Test
    void replaySameEventIsIdempotentAndChangedContentConflicts() {
        String voyage = createVoyage("V-REPLAY");
        EventResponse first = service.ingestEvent("V-REPLAY",
                new IngestEventRequest("evt-1", EventType.BERTH, T0, T0));
        assertFalse(first.replayed());

        EventResponse replay = service.ingestEvent("V-REPLAY",
                new IngestEventRequest("evt-1", EventType.BERTH, T0, T0.plusSeconds(5)));
        assertTrue(replay.replayed());
        assertEquals(first.id(), replay.id());

        ApiException ex = assertThrows(ApiException.class, () -> service.ingestEvent("V-REPLAY",
                new IngestEventRequest("evt-1", EventType.START, T0, T0)));
        assertEquals(409, ex.getStatus().value());

        ApiException timeChanged = assertThrows(ApiException.class,
                () -> service.ingestEvent("V-REPLAY",
                        new IngestEventRequest("evt-1", EventType.BERTH,
                                T0.plusSeconds(1), T0)));
        assertEquals(409, timeChanged.getStatus().value());
    }

    @Test
    void incompleteTimelineRejectsSettlementWithoutCreatingAnyVersion() {
        String voyage = createVoyage("V-INCOMPLETE");
        ingest("V-INCOMPLETE", "berth", EventType.BERTH, T0, T0);

        TimelineException ex = assertThrows(TimelineException.class,
                () -> service.generateSettlement("V-INCOMPLETE", null));
        assertTrue(ex.getMessage().contains("START"));

        assertTrue(settlementRepository.findByVoyageIdOrderByVersionNoAsc(voyage).isEmpty());
    }

    @Test
    void fullLifecycleWithLateEventProducesAdjustmentVersion() {
        String voyage = createVoyage("V-LIFE");
        // 第一版：总跨度 28h，暂停 4h → 已用 24h，刚好不产生滞期
        completeBasicVoyage("V-LIFE", T0.plus(Duration.ofHours(28)), null);

        SettlementResponse v1 = service.generateSettlement("V-LIFE", null);
        assertEquals(1, v1.versionNo());
        assertEquals(SettlementStatus.DRAFT, v1.status());
        assertTrue(v1.current());
        assertEquals(0, new BigDecimal("0.00").compareTo(v1.demurrageAmount()));
        assertEquals("INITIAL", v1.adjustmentReason());

        SettlementResponse confirmed = service.confirmSettlement("V-LIFE", v1.id());
        assertEquals(SettlementStatus.CONFIRMED, confirmed.status());
        Instant confirmedAt = confirmed.confirmedAt();
        assertNotNull(confirmedAt);

        // 迟到事件：补登一个独立的暂停 4 小时（与既有暂停不重叠），已用变为 20h，仍不滞期。
        // 再登记一个把完工后移的迟到完工事件 → 使用新完工（同号内容变更不允许，故改用新暂停演示差额）。
        Instant lateReceived = confirmedAt.plusSeconds(60);
        ingest("V-LIFE", "s2-late", EventType.SUSPEND, T0.plus(Duration.ofHours(20)), lateReceived);
        ingest("V-LIFE", "r2-late", EventType.RESUME, T0.plus(Duration.ofHours(26)), lateReceived);

        SettlementResponse v2 = service.generateSettlement("V-LIFE", "晚到暂停事件 s2-late/r2-late");
        assertEquals(2, v2.versionNo());
        assertEquals(SettlementStatus.DRAFT, v2.status());
        assertTrue(v2.current());
        assertEquals(v1.id(), v2.previousVersionId());
        assertTrue(v2.adjustmentReason().contains("CONFIRMED_V1_ADJUSTMENT"));
        assertTrue(v2.adjustmentReason().contains("s2-late"));
        // 已用 28 - 4 - 6 = 18h
        assertEquals(Duration.ofHours(18).getSeconds(), v2.usedLaytimeSeconds());
        // 原版本金额 0，差额 0
        assertEquals(0, v2.adjustmentDelta().compareTo(BigDecimal.ZERO));

        // 原结算保留为 CONFIRMED 历史，金额未被覆盖
        Settlement old = settlementRepository.findById(v1.id()).orElseThrow();
        assertEquals(SettlementStatus.CONFIRMED, old.getStatus());
        assertFalse(old.isCurrent());
        assertEquals(0, old.getDemurrageAmount().compareTo(BigDecimal.ZERO));
        assertNotNull(old.getConfirmedAt());

        // 查询接口：原始事件、时间线、停算区间、各版结算、差额来源齐全
        VoyageDetailResponse detail = service.getVoyageDetail("V-LIFE");
        assertEquals(7, detail.rawEvents().size());
        assertEquals(2, detail.timeline().suspensions().size());
        assertEquals(2, detail.settlements().size());
        assertEquals(v2.id(), detail.currentSettlement().id());
        assertEquals(v1.eventVersionHash(), detail.settlements().get(0).eventVersionHash());
        assertNotEquals(v1.eventVersionHash(), v2.eventVersionHash());
    }

    @Test
    void lateEventIncreasesDemurrageAndRecordsDelta() {
        String voyage = createVoyage("V-DELTA");
        // 第一版：30h 跨度 - 4h 暂停 = 26h 已用 → 超 2h → 1000 USD
        completeBasicVoyage("V-DELTA", T0.plus(Duration.ofHours(30)), null);
        SettlementResponse v1 = service.generateSettlement("V-DELTA", null);
        service.confirmSettlement("V-DELTA", v1.id());
        assertEquals(new BigDecimal("1000.00"), v1.demurrageAmount());

        // 晚到复工延迟：新增一段独立暂停区间 2h，已用变 24h → 无滞期（负向调整）
        Instant late = Instant.now().plusSeconds(10);
        ingest("V-DELTA", "s3", EventType.SUSPEND, T0.plus(Duration.ofHours(2)), late);
        ingest("V-DELTA", "r3", EventType.RESUME, T0.plus(Duration.ofHours(4)), late);
        SettlementResponse v2 = service.generateSettlement("V-DELTA", null);
        assertEquals(0, new BigDecimal("0.00").compareTo(v2.demurrageAmount()));
        assertEquals(new BigDecimal("-1000.00"), v2.adjustmentDelta());
        assertEquals(new BigDecimal("1000.00"),
                settlementRepository.findById(v1.id()).orElseThrow().getDemurrageAmount());
    }

    @Test
    void failedRecalculationDoesNotChangeConfirmedAmount() {
        String voyage = createVoyage("V-FAIL");
        completeBasicVoyage("V-FAIL", T0.plus(Duration.ofHours(30)), null);
        SettlementResponse v1 = service.generateSettlement("V-FAIL", null);
        service.confirmSettlement("V-FAIL", v1.id());

        // 晚到一个无法解释的事件：没有对应 SUSPEND 的 RESUME
        ingest("V-FAIL", "bad-resume", EventType.RESUME,
                T0.plus(Duration.ofHours(31)), Instant.now().plusSeconds(20));

        assertThrows(TimelineException.class,
                () -> service.generateSettlement("V-FAIL", null));

        Settlement current = settlementRepository.findByVoyageIdAndCurrentTrue(voyage).orElseThrow();
        assertEquals(v1.id(), current.getId());
        assertEquals(SettlementStatus.CONFIRMED, current.getStatus());
        assertEquals(new BigDecimal("1000.00"), current.getDemurrageAmount());
        assertEquals(1, settlementRepository.findByVoyageIdOrderByVersionNoAsc(voyage).size());
    }

    @Test
    void cannotConfirmNonCurrentVersion() {
        String voyage = createVoyage("V-CONFIRM");
        completeBasicVoyage("V-CONFIRM", T0.plus(Duration.ofHours(30)), null);
        SettlementResponse v1 = service.generateSettlement("V-CONFIRM", null);

        // DRAFT 状态下用相同事件重算不产生新版本；制造新版本需要新事件，这里直接验证过期 id
        Instant late = Instant.now().plusSeconds(30);
        ingest("V-CONFIRM", "s9", EventType.SUSPEND, T0.plus(Duration.ofHours(2)), late);
        ingest("V-CONFIRM", "r9", EventType.RESUME, T0.plus(Duration.ofHours(3)), late);
        service.generateSettlement("V-CONFIRM", null);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.confirmSettlement("V-CONFIRM", v1.id()));
        assertEquals(409, ex.getStatus().value());
    }

    @Test
    void draftRecalculationReplacesDraftKeepingHistory() {
        String voyage = createVoyage("V-DRAFT");
        completeBasicVoyage("V-DRAFT", T0.plus(Duration.ofHours(30)), null);
        SettlementResponse v1 = service.generateSettlement("V-DRAFT", null);
        assertEquals(new BigDecimal("1000.00"), v1.demurrageAmount());

        Instant late = Instant.now().plusSeconds(40);
        ingest("V-DRAFT", "s9", EventType.SUSPEND, T0.plus(Duration.ofHours(2)), late);
        ingest("V-DRAFT", "r9", EventType.RESUME, T0.plus(Duration.ofHours(4)), late);
        SettlementResponse v2 = service.generateSettlement("V-DRAFT", null);

        assertEquals(2, v2.versionNo());
        assertEquals(SettlementStatus.DRAFT, v2.status());
        assertEquals(new BigDecimal("0.00"), v2.demurrageAmount());
        Settlement superseded = settlementRepository.findById(v1.id()).orElseThrow();
        assertEquals(SettlementStatus.SUPERSEDED, superseded.getStatus());
        assertTrue(v2.adjustmentReason().startsWith("DRAFT_RECALC"));
    }
}
