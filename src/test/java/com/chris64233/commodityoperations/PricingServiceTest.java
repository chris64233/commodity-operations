package com.chris64233.commodityoperations;

import com.chris64233.commodityoperations.pricing.Contract;
import com.chris64233.commodityoperations.pricing.ContractStatus;
import com.chris64233.commodityoperations.pricing.ContractView;
import com.chris64233.commodityoperations.pricing.Direction;
import com.chris64233.commodityoperations.pricing.PriceFixing;
import com.chris64233.commodityoperations.pricing.PricingException;
import com.chris64233.commodityoperations.pricing.PricingService;
import com.chris64233.commodityoperations.pricing.SettlementEntry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PricingServiceTest {

    private static final Instant NOW = Instant.now();
    private static final AtomicLong VERSION_SEQ = new AtomicLong(1000);

    @Autowired
    private PricingService service;

    private Contract newContract(String quantity, String provisionalPrice) {
        return service.registerContract("COPPER", Direction.SELL, new BigDecimal(quantity), "CNY",
                new BigDecimal(provisionalPrice),
                NOW.minus(1, ChronoUnit.HOURS), NOW.plus(1, ChronoUnit.HOURS),
                Set.of("SHFE", "LME"));
    }

    private void newMarketPrice(String source, String currency, String price, long version,
                                Instant validFrom, Instant validTo) {
        service.registerMarketPrice(source, "COPPER", currency, new BigDecimal(price), version,
                validFrom, validTo);
    }

    private long newValidPrice(String source, String price) {
        long version = VERSION_SEQ.incrementAndGet();
        newMarketPrice(source, "CNY", price, version,
                NOW.minus(30, ChronoUnit.MINUTES), NOW.plus(30, ChronoUnit.MINUTES));
        return version;
    }

    @Test
    void registerContractCreatesProvisionalSettlementWithExactAmount() {
        Contract contract = newContract("3", "0.10");

        ContractView view = service.getContractView(contract.getId());
        assertThat(view.status()).isEqualTo(ContractStatus.PROVISIONAL);
        assertThat(view.remainingQuantity()).isEqualByComparingTo("3");
        assertThat(view.provisionalAmount()).isEqualByComparingTo("0.30");
        assertThat(view.cumulativeAdjustment()).isEqualByComparingTo("0.00");
        assertThat(view.finalAmount()).isNull();
        assertThat(view.fixings()).isEmpty();
    }

    @Test
    void partialFixingConsumesQuantityAndAppendsAdjustmentOnly() {
        Contract contract = newContract("100", "7000.00");
        long version = newValidPrice("SHFE", "7050.00");

        PriceFixing fixing = service.fix(contract.getId(), "FIX-1",
                new BigDecimal("40"), "SHFE", version, new BigDecimal("5.00"));

        assertThat(fixing.getFixedPrice()).isEqualByComparingTo("7055.00");
        ContractView view = service.getContractView(contract.getId());
        assertThat(view.status()).isEqualTo(ContractStatus.PARTIALLY_FIXED);
        assertThat(view.remainingQuantity()).isEqualByComparingTo("60");
        assertThat(view.provisionalAmount()).isEqualByComparingTo("700000.00");
        assertThat(view.cumulativeAdjustment()).isEqualByComparingTo("2200.00");
        assertThat(view.fixings()).hasSize(1);
        assertThat(view.fixings().get(0).source()).isEqualTo("SHFE");
        assertThat(view.fixings().get(0).priceVersion()).isEqualTo(version);
    }

    @Test
    void fixingBeyondRemainingQuantityIsRejectedAndBalanceUnchanged() {
        Contract contract = newContract("100", "7000.00");
        long version = newValidPrice("SHFE", "7050.00");
        service.fix(contract.getId(), "FIX-1", new BigDecimal("80"), "SHFE", version, BigDecimal.ZERO);

        assertThatThrownBy(() -> service.fix(contract.getId(), "FIX-2",
                new BigDecimal("30"), "SHFE", version, BigDecimal.ZERO))
                .isInstanceOf(PricingException.class)
                .hasMessageContaining("未点价数量");

        ContractView view = service.getContractView(contract.getId());
        assertThat(view.remainingQuantity()).isEqualByComparingTo("20");
        assertThat(view.cumulativeAdjustment()).isEqualByComparingTo("4000.00");
    }

    @Test
    void concurrentFixingsOnlyConsumeRemainingQuantityOnce() throws Exception {
        Contract contract = newContract("100", "7000.00");
        long version = newValidPrice("SHFE", "7050.00");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<PriceFixing> task = () -> {
            ready.countDown();
            start.await();
            return service.fix(contract.getId(), "FIX-" + Thread.currentThread().getId(),
                    new BigDecimal("60"), "SHFE", version, BigDecimal.ZERO);
        };
        var executor = Executors.newFixedThreadPool(2);
        Future<PriceFixing> first = executor.submit(task);
        Future<PriceFixing> second = executor.submit(task);
        ready.await();
        start.countDown();

        List<Object> outcomes = new ArrayList<>();
        for (Future<PriceFixing> future : List.of(first, second)) {
            try {
                outcomes.add(future.get());
            } catch (Exception e) {
                outcomes.add(e.getCause());
            }
        }
        executor.shutdown();

        long succeeded = outcomes.stream().filter(o -> o instanceof PriceFixing).count();
        long rejected = outcomes.stream().filter(o -> o instanceof PricingException).count();
        assertThat(succeeded).isEqualTo(1);
        assertThat(rejected).isEqualTo(1);
        ContractView view = service.getContractView(contract.getId());
        assertThat(view.remainingQuantity()).isEqualByComparingTo("40");
        assertThat(view.fixings()).hasSize(1);
    }

    @Test
    void duplicateExternalFixingNoReturnsOriginalWhenContentMatches() {
        Contract contract = newContract("100", "7000.00");
        long version = newValidPrice("SHFE", "7050.00");

        PriceFixing first = service.fix(contract.getId(), "FIX-1",
                new BigDecimal("40"), "SHFE", version, new BigDecimal("5.00"));
        PriceFixing second = service.fix(contract.getId(), "FIX-1",
                new BigDecimal("40"), "SHFE", version, new BigDecimal("5.00"));

        assertThat(second.getId()).isEqualTo(first.getId());
        ContractView view = service.getContractView(contract.getId());
        assertThat(view.fixings()).hasSize(1);
        assertThat(view.remainingQuantity()).isEqualByComparingTo("60");
    }

    @Test
    void duplicateExternalFixingNoWithDifferentContentConflicts() {
        Contract contract = newContract("100", "7000.00");
        long version = newValidPrice("SHFE", "7050.00");
        service.fix(contract.getId(), "FIX-1", new BigDecimal("40"), "SHFE", version, BigDecimal.ZERO);

        assertThatThrownBy(() -> service.fix(contract.getId(), "FIX-1",
                new BigDecimal("50"), "SHFE", version, BigDecimal.ZERO))
                .isInstanceOf(PricingException.class)
                .extracting(e -> ((PricingException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThatThrownBy(() -> service.fix(contract.getId(), "FIX-1",
                new BigDecimal("40"), "SHFE", version, new BigDecimal("1.00")))
                .isInstanceOf(PricingException.class)
                .extracting(e -> ((PricingException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        assertThat(service.getContractView(contract.getId()).fixings()).hasSize(1);
    }

    @Test
    void expiredMarketPriceIsRejectedWithoutChangingBalance() {
        Contract contract = newContract("100", "7000.00");
        newMarketPrice("SHFE", "CNY", "7050.00", VERSION_SEQ.incrementAndGet(),
                NOW.minus(2, ChronoUnit.HOURS), NOW.minus(1, ChronoUnit.HOURS));

        assertThatThrownBy(() -> service.fix(contract.getId(), "FIX-1",
                new BigDecimal("40"), "SHFE", VERSION_SEQ.get(), BigDecimal.ZERO))
                .isInstanceOf(PricingException.class)
                .hasMessageContaining("过期");

        ContractView view = service.getContractView(contract.getId());
        assertThat(view.remainingQuantity()).isEqualByComparingTo("100");
        assertThat(view.fixings()).isEmpty();
    }

    @Test
    void currencyMismatchIsRejectedWithoutChangingBalance() {
        Contract contract = newContract("100", "7000.00");
        long version = VERSION_SEQ.incrementAndGet();
        newMarketPrice("SHFE", "USD", "7050.00", version,
                NOW.minus(30, ChronoUnit.MINUTES), NOW.plus(30, ChronoUnit.MINUTES));

        assertThatThrownBy(() -> service.fix(contract.getId(), "FIX-1",
                new BigDecimal("40"), "SHFE", version, BigDecimal.ZERO))
                .isInstanceOf(PricingException.class)
                .hasMessageContaining("币种");

        assertThat(service.getContractView(contract.getId()).remainingQuantity())
                .isEqualByComparingTo("100");
    }

    @Test
    void fixingOutsideWindowIsRejected() {
        Contract contract = service.registerContract("COPPER", Direction.BUY,
                new BigDecimal("100"), "CNY", new BigDecimal("7000.00"),
                NOW.minus(3, ChronoUnit.HOURS), NOW.minus(1, ChronoUnit.HOURS),
                Set.of("SHFE"));
        long version = newValidPrice("SHFE", "7050.00");

        assertThatThrownBy(() -> service.fix(contract.getId(), "FIX-1",
                new BigDecimal("40"), "SHFE", version, BigDecimal.ZERO))
                .isInstanceOf(PricingException.class)
                .hasMessageContaining("窗口");
    }

    @Test
    void disallowedSourceIsRejected() {
        Contract contract = newContract("100", "7000.00");
        long version = newValidPrice("COMEX", "7050.00");

        assertThatThrownBy(() -> service.fix(contract.getId(), "FIX-1",
                new BigDecimal("40"), "COMEX", version, BigDecimal.ZERO))
                .isInstanceOf(PricingException.class)
                .hasMessageContaining("来源");
    }

    @Test
    void finalSettlementRequiresFullyFixedContract() {
        Contract contract = newContract("100", "7000.00");
        long version = newValidPrice("SHFE", "7050.00");
        service.fix(contract.getId(), "FIX-1", new BigDecimal("40"), "SHFE", version, BigDecimal.ZERO);

        assertThatThrownBy(() -> service.finalizeSettlement(contract.getId()))
                .isInstanceOf(PricingException.class)
                .hasMessageContaining("未点价");
    }

    @Test
    void finalSettlementSumsProvisionalAndAdjustmentsExactly() {
        Contract contract = newContract("3", "0.10");
        long shfe = newValidPrice("SHFE", "0.20");
        long lme = newValidPrice("LME", "0.15");
        service.fix(contract.getId(), "FIX-1", new BigDecimal("1"), "SHFE", shfe, BigDecimal.ZERO);
        service.fix(contract.getId(), "FIX-2", new BigDecimal("2"), "LME", lme, BigDecimal.ZERO);

        SettlementEntry finalEntry = service.finalizeSettlement(contract.getId());

        assertThat(finalEntry.getAmount()).isEqualByComparingTo("0.50");
        ContractView view = service.getContractView(contract.getId());
        assertThat(view.status()).isEqualTo(ContractStatus.FINAL_SETTLED);
        assertThat(view.finalAmount()).isEqualByComparingTo("0.50");
        assertThat(view.provisionalAmount()).isEqualByComparingTo("0.30");
        assertThat(view.cumulativeAdjustment()).isEqualByComparingTo("0.20");
    }

    @Test
    void lateFixingAfterFinalSettlementIsRejected() {
        Contract contract = newContract("100", "7000.00");
        long version = newValidPrice("SHFE", "7050.00");
        service.fix(contract.getId(), "FIX-1", new BigDecimal("100"), "SHFE", version, BigDecimal.ZERO);
        service.finalizeSettlement(contract.getId());

        assertThatThrownBy(() -> service.fix(contract.getId(), "FIX-LATE",
                new BigDecimal("1"), "SHFE", version, BigDecimal.ZERO))
                .isInstanceOf(PricingException.class)
                .hasMessageContaining("最终结算");
    }

    @Test
    void concurrentFinalSettlementAndLateFixingYieldSingleConsistentResult() throws Exception {
        Contract contract = newContract("100", "7000.00");
        long version = newValidPrice("SHFE", "7050.00");
        service.fix(contract.getId(), "FIX-1", new BigDecimal("100"), "SHFE", version, BigDecimal.ZERO);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Object> finalizeTask = () -> {
            ready.countDown();
            start.await();
            return service.finalizeSettlement(contract.getId());
        };
        Callable<Object> lateFixTask = () -> {
            ready.countDown();
            start.await();
            return service.fix(contract.getId(), "FIX-LATE",
                    new BigDecimal("1"), "SHFE", version, BigDecimal.ZERO);
        };
        var executor = Executors.newFixedThreadPool(2);
        Future<Object> finalFuture = executor.submit(finalizeTask);
        Future<Object> fixFuture = executor.submit(lateFixTask);
        ready.await();
        start.countDown();

        SettlementEntry finalEntry = (SettlementEntry) finalFuture.get();
        assertThatThrownBy(fixFuture::get).hasCauseInstanceOf(PricingException.class);
        executor.shutdown();

        SettlementEntry secondCall = service.finalizeSettlement(contract.getId());
        assertThat(secondCall.getId()).isEqualTo(finalEntry.getId());
        ContractView view = service.getContractView(contract.getId());
        assertThat(view.finalAmount()).isEqualByComparingTo("705000.00");
        assertThat(view.fixings()).hasSize(1);
    }

    @Test
    void amountsRemainExactWithoutFloatingPointError() {
        Contract contract = newContract("0.3", "0.1");
        long version = newValidPrice("SHFE", "0.3");
        service.fix(contract.getId(), "FIX-1", new BigDecimal("0.3"), "SHFE", version, BigDecimal.ZERO);
        SettlementEntry finalEntry = service.finalizeSettlement(contract.getId());

        assertThat(finalEntry.getAmount()).isEqualByComparingTo("0.09");
        ContractView view = service.getContractView(contract.getId());
        assertThat(view.cumulativeAdjustment()).isEqualByComparingTo("0.06");
    }
}
