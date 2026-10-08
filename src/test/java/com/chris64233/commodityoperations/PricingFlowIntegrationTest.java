package com.chris64233.commodityoperations;

import com.chris64233.commodityoperations.domain.ContractDirection;
import com.chris64233.commodityoperations.dto.*;
import com.chris64233.commodityoperations.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.chris64233.commodityoperations.repo.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PricingFlowIntegrationTest {

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

    private static final String SOURCE = "LME";

    private RegisterContractRequest contractRequest(String no, String currency,
                                                     BigDecimal qty, BigDecimal provisional) {
        Instant now = Instant.now();
        return new RegisterContractRequest(no, "COPPER", ContractDirection.BUY, qty, currency,
                provisional, now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS),
                Set.of(SOURCE, "SHFE"));
    }

    private void publishPrice(String version, String currency, BigDecimal price,
                              Instant from, Instant to) {
        marketPriceService.publish(new PublishMarketPriceRequest(
                SOURCE, version, "COPPER", currency, price, from, to));
    }

    private SubmitPricingRequest pricing(String externalNo, BigDecimal qty,
                                         String version, BigDecimal premium) {
        return new SubmitPricingRequest(externalNo, qty, SOURCE, version,
                premium, Instant.now());
    }

    @Test
    void registerCreatesProvisionalVersionWithExactAmount() {
        ContractView view = contractService.register(
                contractRequest("C-REG-01", "USD", new BigDecimal("100.00000003"),
                        new BigDecimal("9999.99999999")));

        assertThat(view.status()).isEqualTo("OPEN");
        assertThat(view.unpricedQty()).isEqualByComparingTo("100.00000003");
        assertThat(view.provisionalAmount())
                .isEqualByComparingTo("1000000.000299");
        assertThat(view.settlementVersions()).hasSize(1);
        assertThat(view.settlementVersions().get(0).type()).isEqualTo("PROVISIONAL");
        assertThat(view.cumulativeAdjustment()).isEqualByComparingTo("0");
    }

    @Test
    void partialPricingAppendsVersionWithoutTouchingHistory() {
        Instant now = Instant.now();
        publishPrice("V1", "USD", new BigDecimal("10100.00"),
                now.minus(2, ChronoUnit.DAYS), now.plus(2, ChronoUnit.DAYS));
        ContractView created = contractService.register(
                contractRequest("C-PART-01", "USD", new BigDecimal("100"),
                        new BigDecimal("10000.00")));

        ContractView after = pricingService.submitPricing(created.id(),
                pricing("P-001", new BigDecimal("40"), "V1", new BigDecimal("5.50")));

        assertThat(after.unpricedQty()).isEqualByComparingTo("60");
        assertThat(after.pricedQty()).isEqualByComparingTo("40");
        assertThat(after.status()).isEqualTo("OPEN");
        assertThat(after.settlementVersions()).hasSize(2);

        SettlementVersionView provisional = after.settlementVersions().get(0);
        SettlementVersionView adjustment = after.settlementVersions().get(1);
        assertThat(provisional.type()).isEqualTo("PROVISIONAL");
        assertThat(provisional.totalAmount()).isEqualByComparingTo("1000000");
        assertThat(adjustment.type()).isEqualTo("PRICING_ADJUSTMENT");
        // (10100 + 5.5 - 10000) * 40 = 4220
        assertThat(adjustment.deltaAmount()).isEqualByComparingTo("4220.000000");
        assertThat(adjustment.totalAmount()).isEqualByComparingTo("1004220.000000");
        assertThat(after.cumulativeAdjustment()).isEqualByComparingTo("4220");

        PricingSnapshotView snapshot = after.pricings().get(0);
        assertThat(snapshot.marketPrice()).isEqualByComparingTo("10100.00");
        assertThat(snapshot.fixedPrice()).isEqualByComparingTo("10105.50");
    }

    @Test
    void quantityExceedingUnpricedIsRejectedAndBalanceUnchanged() {
        Instant now = Instant.now();
        publishPrice("V1", "USD", new BigDecimal("10100.00"),
                now.minus(2, ChronoUnit.DAYS), now.plus(2, ChronoUnit.DAYS));
        ContractView created = contractService.register(
                contractRequest("C-OVER-01", "USD", new BigDecimal("100"),
                        new BigDecimal("10000")));
        pricingService.submitPricing(created.id(),
                pricing("P-100", new BigDecimal("60"), "V1", BigDecimal.ZERO));

        assertThatThrownBy(() -> pricingService.submitPricing(created.id(),
                pricing("P-101", new BigDecimal("40.00000001"), "V1", BigDecimal.ZERO)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("超过未点价数量");

        ContractView view = contractService.getById(created.id());
        assertThat(view.pricedQty()).isEqualByComparingTo("60");
        assertThat(view.unpricedQty()).isEqualByComparingTo("40");
        assertThat(view.settlementVersions()).hasSize(2);
    }

    @Test
    void duplicateExternalPricingNoIsIdempotentButChangedContentConflicts() {
        Instant now = Instant.now();
        publishPrice("V1", "USD", new BigDecimal("10100"),
                now.minus(2, ChronoUnit.DAYS), now.plus(2, ChronoUnit.DAYS));
        publishPrice("V2", "USD", new BigDecimal("10200"),
                now.minus(2, ChronoUnit.DAYS), now.plus(2, ChronoUnit.DAYS));
        ContractView created = contractService.register(
                contractRequest("C-IDEM-01", "USD", new BigDecimal("100"),
                        new BigDecimal("10000")));

        SubmitPricingRequest first = pricing("DUP-1", new BigDecimal("10"), "V1",
                new BigDecimal("1.25"));
        ContractView once = pricingService.submitPricing(created.id(), first);
        ContractView twice = pricingService.submitPricing(created.id(),
                pricing("DUP-1", new BigDecimal("10"), "V1", new BigDecimal("1.25")));

        assertThat(twice.pricings()).hasSize(1);
        assertThat(twice.pricedQty()).isEqualByComparingTo("10");
        assertThat(twice.settlementVersions()).hasSize(once.settlementVersions().size());

        assertThatThrownBy(() -> pricingService.submitPricing(created.id(),
                pricing("DUP-1", new BigDecimal("11"), "V1", new BigDecimal("1.25"))))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> pricingService.submitPricing(created.id(),
                pricing("DUP-1", new BigDecimal("10"), "V2", new BigDecimal("1.25"))))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> pricingService.submitPricing(created.id(),
                pricing("DUP-1", new BigDecimal("10"), "V1", new BigDecimal("1.26"))))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void expiredPriceCurrencyMismatchAndWindowViolationAreRejected() {
        Instant now = Instant.now();
        publishPrice("EXPIRED", "USD", new BigDecimal("10100"),
                now.minus(4, ChronoUnit.DAYS), now.minus(1, ChronoUnit.MINUTES));
        publishPrice("FUTURE", "USD", new BigDecimal("10100"),
                now.plus(1, ChronoUnit.MINUTES), now.plus(4, ChronoUnit.DAYS));
        publishPrice("EUR-V", "EUR", new BigDecimal("9500"),
                now.minus(2, ChronoUnit.DAYS), now.plus(2, ChronoUnit.DAYS));
        publishPrice("VALID", "USD", new BigDecimal("10100"),
                now.minus(3, ChronoUnit.DAYS), now.plus(3, ChronoUnit.DAYS));

        ContractView usdContract = contractService.register(
                contractRequest("C-VAL-01", "USD", new BigDecimal("100"),
                        new BigDecimal("10000")));

        assertThatThrownBy(() -> pricingService.submitPricing(usdContract.id(),
                pricing("E-1", new BigDecimal("10"), "EXPIRED", BigDecimal.ZERO)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("过期");
        assertThatThrownBy(() -> pricingService.submitPricing(usdContract.id(),
                pricing("E-2", new BigDecimal("10"), "FUTURE", BigDecimal.ZERO)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("过期或尚未生效");
        assertThatThrownBy(() -> pricingService.submitPricing(usdContract.id(),
                pricing("E-3", new BigDecimal("10"), "EUR-V", BigDecimal.ZERO)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("币种");
        assertThatThrownBy(() -> pricingService.submitPricing(usdContract.id(),
                new SubmitPricingRequest("E-4", new BigDecimal("10"), SOURCE, "EXPIRED",
                        BigDecimal.ZERO, now.plus(2, ChronoUnit.DAYS))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("过期");
        assertThatThrownBy(() -> pricingService.submitPricing(usdContract.id(),
                new SubmitPricingRequest("E-5", new BigDecimal("10"), SOURCE, "VALID",
                        BigDecimal.ZERO, now.plus(2, ChronoUnit.DAYS))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("点价窗口");
        assertThatThrownBy(() -> pricingService.submitPricing(usdContract.id(),
                new SubmitPricingRequest("E-6", new BigDecimal("10"), SOURCE, "VALID",
                        BigDecimal.ZERO, now.minus(2, ChronoUnit.DAYS))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("点价窗口");

        ContractView unchanged = contractService.getById(usdContract.id());
        assertThat(unchanged.pricedQty()).isEqualByComparingTo("0");
        assertThat(unchanged.settlementVersions()).hasSize(1);
    }

    @Test
    void disallowedSourceAndUnknownVersionAreRejected() {
        Instant now = Instant.now();
        marketPriceService.publish(new PublishMarketPriceRequest("CME", "CME-1", "COPPER",
                "USD", new BigDecimal("10100"),
                now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS)));
        ContractView created = contractService.register(
                contractRequest("C-SRC-01", "USD", new BigDecimal("100"),
                        new BigDecimal("10000")));

        assertThatThrownBy(() -> pricingService.submitPricing(created.id(),
                new SubmitPricingRequest("S-1", new BigDecimal("10"), "CME", "CME-1",
                        BigDecimal.ZERO, Instant.now())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("允许列表");
        assertThatThrownBy(() -> pricingService.submitPricing(created.id(),
                pricing("S-2", new BigDecimal("10"), "MISSING", BigDecimal.ZERO)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("不存在");
    }

    @Test
    void finalSettlementOnlyAfterFullyPricedAndRejectsLatePricing() {
        Instant now = Instant.now();
        publishPrice("V1", "USD", new BigDecimal("10100"),
                now.minus(2, ChronoUnit.DAYS), now.plus(2, ChronoUnit.DAYS));
        ContractView created = contractService.register(
                contractRequest("C-FIN-01", "USD", new BigDecimal("100"),
                        new BigDecimal("10000")));

        pricingService.submitPricing(created.id(),
                pricing("F-1", new BigDecimal("60"), "V1", new BigDecimal("10")));

        assertThatThrownBy(() -> settlementService.finalSettle(created.id()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("未点价数量");

        pricingService.submitPricing(created.id(),
                pricing("F-2", new BigDecimal("40"), "V1", new BigDecimal("-5")));
        ContractView finalView = settlementService.finalSettle(created.id());

        assertThat(finalView.status()).isEqualTo("FINALLY_SETTLED");
        assertThat(finalView.unpricedQty()).isEqualByComparingTo("0");
        // 60 * 110 + 40 * 95 = 6600 + 3800 = 10400 adjustment
        assertThat(finalView.cumulativeAdjustment()).isEqualByComparingTo("10400");
        assertThat(finalView.finalAmount()).isEqualByComparingTo("1010400.000000");
        assertThat(finalView.settlementVersions().get(
                finalView.settlementVersions().size() - 1).type()).isEqualTo("FINAL");

        ContractView idempotent = settlementService.finalSettle(created.id());
        assertThat(idempotent.settlementVersions()
                .stream().filter(v -> v.type().equals("FINAL")).count()).isEqualTo(1);

        assertThatThrownBy(() -> pricingService.submitPricing(created.id(),
                pricing("F-LATE", new BigDecimal("1"), "V1", BigDecimal.ZERO)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("最终结算");
    }
}
