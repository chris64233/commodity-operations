package com.chris64233.commodityoperations.laytime.service;

import com.chris64233.commodityoperations.laytime.domain.EventType;
import com.chris64233.commodityoperations.laytime.domain.StopRule;
import com.chris64233.commodityoperations.laytime.exception.SettlementConflictException;
import com.chris64233.commodityoperations.laytime.service.SnapshotDtos.SettlementView;
import com.chris64233.commodityoperations.laytime.web.EventRequest;
import com.chris64233.commodityoperations.laytime.web.VoyageRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 重算与结算确认同时发生：只有基于最新完整事件版本的结果能成为当前版本。
 */
@SpringBootTest
class LaytimeConcurrencyTest {

    private static final Instant T0 = Instant.parse("2026-10-02T00:00:00Z");

    @Autowired
    private LaytimeService service;

    private void seed(String no) {
        service.registerVoyage(new VoyageRequest(
                no, "PT6H", new BigDecimal("100.00"), "USD",
                StopRule.DEDUCT_PAUSE_INTERVALS.name()));
        ingest(no, "e1", EventType.BERTH, 8);
        ingest(no, "e2", EventType.START, 10);
        ingest(no, "e5", EventType.COMPLETE, 18);
        service.generateSettlement(no, null);
        // 迟到事件先落库但不重算：草稿 V1 固定在事件版本 3，最新版本为 5
        ingest(no, "e3", EventType.PAUSE, 12);
        ingest(no, "e4", EventType.RESUME, 14);
    }

    private void ingest(String no, String externalNo, EventType type, int hours) {
        service.ingestEvent(no,
                new EventRequest(externalNo, type.name(), T0.plusSeconds(hours * 3600L)));
    }

    @Test
    void 重算与确认并发时只有最新版本结果成为当前版本() throws Exception {
        for (int i = 0; i < 5; i++) {
            runOnce("V-CONC-" + i);
        }
    }

    private void runOnce(String no) throws Exception {
        seed(no);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);

        Future<SettlementView> staleConfirm = pool.submit(() -> {
            barrier.await();
            return service.confirmSettlement(no, 1);
        });
        Future<SettlementView> recalculate = pool.submit(() -> {
            barrier.await();
            return service.generateSettlement(no, "并发场景：补传停工事件");
        });

        SettlementView recalculated;
        try {
            recalculated = recalculate.get();
        } catch (ExecutionException e) {
            throw new AssertionError("重算不应失败", e);
        }
        boolean confirmWonRace = false;
        try {
            staleConfirm.get();
            confirmWonRace = true;
        } catch (ExecutionException e) {
            assertThat(e.getCause()).isInstanceOf(SettlementConflictException.class);
        }
        pool.shutdown();

        assertThat(recalculated.versionNo()).isEqualTo(2);
        assertThat(recalculated.usedSeconds()).isEqualTo(6 * 3600L);
        assertThat(recalculated.demurrageAmount()).isEqualByComparingTo("0.00");

        SettlementView current = service.getVoyageDetail(no).settlements().stream()
                .filter(SettlementView::current)
                .findFirst().orElseThrow();
        // 无论谁先拿到锁，当前版本只能是基于最新完整事件版本的 V2
        assertThat(current.versionNo()).isEqualTo(2);
        assertThat(current.pinnedEventVersion()).isEqualTo(5);

        SettlementView v1 = service.getVoyageDetail(no).settlements().get(0);
        assertThat(v1.versionNo()).isEqualTo(1);
        assertThat(v1.current()).isFalse();
        assertThat(v1.demurrageAmount()).isEqualByComparingTo("200.00");

        if (confirmWonRace) {
            // 确认先串行执行：V1 当时固定在最新版本，确认合法；重算随后生成 V2 差额调整草稿
            assertThat(v1.status()).isEqualTo("CONFIRMED");
            assertThat(current.status()).isEqualTo("DRAFT");
            assertThat(current.adjustmentReason()).isEqualTo("并发场景：补传停工事件");
            SettlementView confirmedNow = service.confirmSettlement(no, null);
            assertThat(confirmedNow.versionNo()).isEqualTo(2);
            assertThat(confirmedNow.status()).isEqualTo("CONFIRMED");
        } else {
            // 重算先串行执行：过期 V1 草稿被取代，其确认必须失败
            assertThat(v1.status()).isEqualTo("DRAFT");
            assertThat(current.status()).isEqualTo("DRAFT");
            SettlementView confirmedNow = service.confirmSettlement(no, null);
            assertThat(confirmedNow.versionNo()).isEqualTo(2);
            assertThat(confirmedNow.status()).isEqualTo("CONFIRMED");
        }
    }
}
