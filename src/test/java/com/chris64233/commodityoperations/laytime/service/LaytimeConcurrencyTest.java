package com.chris64233.commodityoperations.laytime.service;

import com.chris64233.commodityoperations.laytime.domain.CountingBasis;
import com.chris64233.commodityoperations.laytime.domain.EventType;
import com.chris64233.commodityoperations.laytime.domain.Settlement;
import com.chris64233.commodityoperations.laytime.domain.SettlementStatus;
import com.chris64233.commodityoperations.laytime.dto.Requests.CreateVoyageRequest;
import com.chris64233.commodityoperations.laytime.dto.Requests.IngestEventRequest;
import com.chris64233.commodityoperations.laytime.repo.SettlementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LaytimeConcurrencyTest {

    @Autowired
    private LaytimeService service;
    @Autowired
    private SettlementRepository settlementRepository;

    private static final Instant T0 = Instant.parse("2026-10-02T00:00:00Z");

    private String seedVoyage(String code, Instant completeAt) {
        service.createVoyage(new CreateVoyageRequest(code, "MV C", Duration.ofHours(24).toSeconds(),
                new BigDecimal("12000"), "USD", CountingBasis.ON_BERTH));
        service.ingestEvent(code, new IngestEventRequest("berth", EventType.BERTH, T0, T0));
        service.ingestEvent(code, new IngestEventRequest("start", EventType.START,
                T0.plus(Duration.ofHours(1)), T0));
        service.ingestEvent(code, new IngestEventRequest("done", EventType.COMPLETE, completeAt, completeAt));
        return code;
    }

    /**
     * 结算确认与重算并发：只有一个操作能完成，最终恰好存在一个当前版本；
     * 若确认胜出，则 CONFIRMED 版本保持当前，重算必须新建调整版本而不是覆盖；
     * 若重算胜出，新版本为当前，旧版本被 SUPERSEDED。
     */
    @Test
    void confirmAndRecalculateAreSerialized() throws Exception {
        for (int iter = 0; iter < 5; iter++) {
            String code = "V-CONC-" + iter + "-" + UUID.randomUUID().toString().substring(0, 8);
            seedVoyage(code, T0.plus(Duration.ofHours(30))); // 已用 30h，超 6h
            String voyageId = service.getVoyageDetail(code).voyage().id();
            var v1 = service.generateSettlement(code, null);

            // 晚到事件：2h 额外暂停
            Instant late = Instant.now().plusSeconds(60);
            service.ingestEvent(code, new IngestEventRequest("s-late",
                    EventType.SUSPEND, T0.plus(Duration.ofHours(3)), late));
            service.ingestEvent(code, new IngestEventRequest("r-late",
                    EventType.RESUME, T0.plus(Duration.ofHours(5)), late));

            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger failures = new AtomicInteger();
            ExecutorService pool = Executors.newFixedThreadPool(2);

            Runnable confirmTask = () -> {
                try {
                    await(start);
                    service.confirmSettlement(code, v1.id());
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            };
            Runnable recalcTask = () -> {
                try {
                    await(start);
                    service.generateSettlement(code, "并发重算");
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            };

            pool.submit(recalcTask);
            pool.submit(confirmTask);
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "并发任务应在超时前结束");

            List<Settlement> all = settlementRepository.findByVoyageIdOrderByVersionNoAsc(voyageId);
            long currentCount = all.stream().filter(Settlement::isCurrent).count();
            assertEquals(1, currentCount, "同一航次必须恰好有一个当前版本");

            long confirmedCount = all.stream()
                    .filter(s -> s.getStatus() == SettlementStatus.CONFIRMED).count();
            assertTrue(confirmedCount <= 1, "最多一个 CONFIRMED 版本");

            Settlement current = settlementRepository.findByVoyageIdAndCurrentTrue(voyageId).orElseThrow();
            if (confirmedCount == 1) {
                Settlement confirmed = all.stream()
                        .filter(s -> s.getStatus() == SettlementStatus.CONFIRMED).findFirst().orElseThrow();
                if (confirmed.getVersionNo() == 1) {
                    // 确认先持锁：v1(2500) 冻结保留，重算随后生成 v2(1500) 调整版本
                    assertEquals(new BigDecimal("3000.00"), confirmed.getDemurrageAmount());
                    assertEquals(2, current.getVersionNo());
                    assertEquals(SettlementStatus.DRAFT, current.getStatus());
                    assertEquals(new BigDecimal("2000.00"), current.getDemurrageAmount());
                    assertEquals(new BigDecimal("-1000.00"), current.getAdjustmentDelta());
                } else {
                    // 重算先持锁：v1 被 SUPERSEDED，基于最新事件的 v2(1500) 成为当前并被确认
                    assertEquals(2, confirmed.getVersionNo());
                    assertEquals(2, current.getVersionNo());
                    assertEquals(new BigDecimal("2000.00"), current.getDemurrageAmount());
                    Settlement firstVersion = all.get(0);
                    assertEquals(SettlementStatus.SUPERSEDED, firstVersion.getStatus());
                    assertEquals(new BigDecimal("3000.00"), firstVersion.getDemurrageAmount());
                }
            } else {
                // 无 CONFIRMED：重算先生成 v2，确认线程因指定了过期 v1 id 而冲突失败
                assertEquals(2, current.getVersionNo());
                assertEquals(new BigDecimal("2000.00"), current.getDemurrageAmount());
            }
            // 任何失败都必须是干净的业务失败，不能留下两个 current 或金额被破坏
            assertNotNull(current.getDemurrageAmount());
        }
    }

    /** 两个不同线程同时提交相同外部事件号：至多入库一条，结果幂等。 */
    @Test
    void concurrentDuplicateEventSubmissionsDoNotCorrupt() throws Exception {
        String code = "V-EVT-CONC-" + UUID.randomUUID().toString().substring(0, 8);
        service.createVoyage(new CreateVoyageRequest(code, "MV E", Duration.ofHours(24).toSeconds(),
                new BigDecimal("12000"), "USD", CountingBasis.ON_BERTH));

        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger conflicts = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    await(start);
                    service.ingestEvent(code, new IngestEventRequest(
                            "same-no", EventType.BERTH, T0, T0));
                } catch (ApiException e) {
                    if (e.getStatus().value() == 409) {
                        conflicts.incrementAndGet();
                    } else {
                        throw e;
                    }
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        // 只有一个线程成功插入；其余线程要么读到已存在记录走幂等，要么拿到唯一约束冲突
        assertEquals(1, service.getVoyageDetail(code).rawEvents().size());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
