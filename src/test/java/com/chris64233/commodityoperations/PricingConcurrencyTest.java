package com.chris64233.commodityoperations;

import com.chris64233.commodityoperations.domain.ContractDirection;
import com.chris64233.commodityoperations.dto.ContractView;
import com.chris64233.commodityoperations.dto.PublishMarketPriceRequest;
import com.chris64233.commodityoperations.dto.RegisterContractRequest;
import com.chris64233.commodityoperations.dto.SubmitPricingRequest;
import com.chris64233.commodityoperations.service.BusinessRuleException;
import com.chris64233.commodityoperations.service.ContractService;
import com.chris64233.commodityoperations.service.MarketPriceService;
import com.chris64233.commodityoperations.service.PricingService;
import com.chris64233.commodityoperations.service.SettlementService;
import com.chris64233.commodityoperations.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PricingConcurrencyTest {

    @Autowired
    private ContractService contractService;
    @Autowired
    private PricingService pricingService;
    @Autowired
    private SettlementService settlementService;
    @Autowired
    private MarketPriceService marketPriceService;
    @Autowired
    private SettlementVersionRepository settlementVersionRepository;
    @Autowired
    private PricingOrderRepository pricingOrderRepository;
    @Autowired
    private ContractRepository contractRepository;
    @Autowired
    private MarketPriceRepository marketPriceRepository;

    @BeforeEach
    void cleanDatabase() {
        settlementVersionRepository.deleteAllInBatch();
        pricingOrderRepository.deleteAllInBatch();
        contractRepository.deleteAllInBatch();
        marketPriceRepository.deleteAllInBatch();
    }

    private Long setupContract(String no, BigDecimal qty) {
        Instant now = Instant.now();
        marketPriceService.publish(new PublishMarketPriceRequest(
                "LME", "CV-1", "COPPER", "USD", new BigDecimal("10100"),
                now.minus(2, ChronoUnit.DAYS), now.plus(2, ChronoUnit.DAYS)));
        ContractView view = contractService.register(new RegisterContractRequest(
                no, "COPPER", ContractDirection.SELL, qty, "USD", new BigDecimal("10000"),
                now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS),
                Set.of("LME")));
        return view.id();
    }

    private SubmitPricingRequest request(String externalNo, String qty) {
        return new SubmitPricingRequest(externalNo, new BigDecimal(qty), "LME", "CV-1",
                BigDecimal.ZERO, Instant.now());
    }

    @Test
    void concurrentPricingsOnlyConsumeUnpricedQuantity() throws Exception {
        Long contractId = setupContract("C-CONC-01", new BigDecimal("100"));
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int index = i;
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    pricingService.submitPricing(
                            contractId, request("CC-" + index, "15"));
                    success.incrementAndGet();
                } catch (BusinessRuleException ex) {
                    rejected.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();

        ContractView view = contractService.getById(contractId);
        // 100 / 15 = 6 次成功（90），4 次因余额不足被拒；绝不会超额点价。
        assertThat(success.get()).isEqualTo(6);
        assertThat(rejected.get()).isEqualTo(4);
        assertThat(view.pricedQty()).isEqualByComparingTo("90");
        assertThat(view.unpricedQty()).isEqualByComparingTo("10");
        assertThat(view.pricings()).hasSize(6);
        // 暂定 + 6 个调整版本，顺序连续无覆盖。
        assertThat(view.settlementVersions()).hasSize(7);
        assertThat(view.settlementVersions().get(0).type()).isEqualTo("PROVISIONAL");
    }

    @Test
    void concurrentSameExternalPricingNoProducesSingleResult() throws Exception {
        Long contractId = setupContract("C-CONC-02", new BigDecimal("100"));
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();

        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    pricingService.submitPricing(contractId, request("SAME-NO", "20"));
                    success.incrementAndGet();
                } catch (BusinessRuleException ex) {
                    conflict.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();

        ContractView view = contractService.getById(contractId);
        assertThat(view.pricings()).hasSize(1);
        assertThat(view.pricedQty()).isEqualByComparingTo("20");
        // 悲观锁串行化后，后续请求均命中“同号同内容返回原结果”的幂等分支，
        // 因而不会产生冲突；无论如何只允许存在一笔点价。
        assertThat(success.get()).isEqualTo(threads);
        assertThat(conflict.get()).isZero();
    }

    @Test
    void finalSettlementRacingWithLatePricingHasOneOutcome() throws Exception {
        Long contractId = setupContract("C-CONC-03", new BigDecimal("100"));
        pricingService.submitPricing(contractId, request("RC-1", "60"));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<?> lastPricing = pool.submit(() -> {
            start.await();
            pricingService.submitPricing(contractId, request("RC-LATE", "40"));
            return null;
        });
        Future<?> finalSettle = pool.submit(() -> {
            start.await();
            // 点价线程先补齐最后 40 数量后，最终结算应成功；
            // 若最终结算先到，则因仍有未点价数量被拒，随后可再次结算。
            try {
                settlementService.finalSettle(contractId);
            } catch (BusinessRuleException ignored) {
            }
            return null;
        });
        start.countDown();
        lastPricing.get();
        finalSettle.get();
        pool.shutdown();

        ContractView settled = settlementService.finalSettle(contractId);
        assertThat(settled.status()).isEqualTo("FINALLY_SETTLED");
        assertThat(settled.pricedQty()).isEqualByComparingTo("100");
        assertThat(settled.finalAmount()).isEqualByComparingTo("1010000.000000");
        long finalCount = settled.settlementVersions().stream()
                .filter(v -> v.type().equals("FINAL")).count();
        assertThat(finalCount).isEqualTo(1);
    }
}
