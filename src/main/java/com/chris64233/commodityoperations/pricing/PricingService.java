package com.chris64233.commodityoperations.pricing;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

@Service
public class PricingService {

    private final ContractRepository contractRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final PriceFixingRepository fixingRepository;
    private final SettlementEntryRepository settlementRepository;
    private final Clock clock;

    public PricingService(ContractRepository contractRepository,
                          MarketPriceRepository marketPriceRepository,
                          PriceFixingRepository fixingRepository,
                          SettlementEntryRepository settlementRepository,
                          Clock clock) {
        this.contractRepository = contractRepository;
        this.marketPriceRepository = marketPriceRepository;
        this.fixingRepository = fixingRepository;
        this.settlementRepository = settlementRepository;
        this.clock = clock;
    }

    @Transactional
    public Contract registerContract(String commodity, Direction direction, BigDecimal quantity,
                                     String currency, BigDecimal provisionalPrice,
                                     Instant windowStart, Instant windowEnd, Set<String> allowedSources) {
        if (quantity == null || quantity.signum() <= 0) {
            throw PricingException.rejected("合同数量必须为正数");
        }
        if (provisionalPrice == null || provisionalPrice.signum() < 0) {
            throw PricingException.rejected("暂定价格不能为负");
        }
        if (windowStart == null || windowEnd == null || !windowStart.isBefore(windowEnd)) {
            throw PricingException.rejected("点价窗口无效");
        }
        if (allowedSources == null || allowedSources.isEmpty()) {
            throw PricingException.rejected("至少允许一个市场价格来源");
        }
        Contract contract = contractRepository.save(new Contract(
                commodity, direction, quantity, currency, provisionalPrice,
                windowStart, windowEnd, allowedSources));
        BigDecimal provisionalAmount = money(provisionalPrice.multiply(quantity));
        settlementRepository.save(new SettlementEntry(contract, 1, SettlementEntryType.PROVISIONAL,
                quantity, provisionalPrice, provisionalAmount, null, now()));
        return contract;
    }

    @Transactional
    public MarketPrice registerMarketPrice(String source, String commodity, String currency,
                                           BigDecimal price, long priceVersion,
                                           Instant validFrom, Instant validTo) {
        if (price == null || price.signum() < 0) {
            throw PricingException.rejected("市场价格不能为负");
        }
        if (validFrom == null || validTo == null || validFrom.isAfter(validTo)) {
            throw PricingException.rejected("市场价格有效期无效");
        }
        marketPriceRepository.findBySourceAndCommodityAndCurrencyAndPriceVersion(
                source, commodity, currency, priceVersion).ifPresent(existing -> {
            throw PricingException.conflict("市场价格版本已存在: " + source + " v" + priceVersion);
        });
        return marketPriceRepository.save(new MarketPrice(
                source, commodity, currency, price, priceVersion, validFrom, validTo));
    }

    @Transactional
    public PriceFixing fix(Long contractId, String externalFixingNo, BigDecimal quantity,
                           String source, long priceVersion, BigDecimal basis) {
        if (externalFixingNo == null || externalFixingNo.isBlank()) {
            throw PricingException.rejected("外部点价号不能为空");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw PricingException.rejected("点价数量必须为正数");
        }
        if (basis == null) {
            throw PricingException.rejected("升贴水不能为空");
        }
        String requestHash = hash(externalFixingNo, quantity, source, priceVersion, basis);

        Contract contract = contractRepository.findByIdForUpdate(contractId)
                .orElseThrow(() -> PricingException.notFound("合同不存在: " + contractId));

        var existing = fixingRepository.findByContractIdAndExternalFixingNo(contractId, externalFixingNo);
        if (existing.isPresent()) {
            PriceFixing fixing = existing.get();
            if (fixing.getRequestHash().equals(requestHash)) {
                return fixing;
            }
            throw PricingException.conflict("外部点价号已使用且内容不一致: " + externalFixingNo);
        }

        if (contract.getStatus() == ContractStatus.FINAL_SETTLED) {
            throw PricingException.rejected("合同已完成最终结算，拒绝迟到点价");
        }

        Instant now = now();
        if (now.isBefore(contract.getPricingWindowStart()) || now.isAfter(contract.getPricingWindowEnd())) {
            throw PricingException.rejected("当前时间超出点价窗口");
        }
        if (!contract.getAllowedSources().contains(source)) {
            throw PricingException.rejected("市场价格来源不在合同允许范围内: " + source);
        }
        MarketPrice marketPrice = marketPriceRepository
                .findBySourceAndCommodityAndCurrencyAndPriceVersion(
                        source, contract.getCommodity(), contract.getCurrency(), priceVersion)
                .orElseThrow(() -> PricingException.rejected(
                        "市场价格不存在或币种不符: " + source + " v" + priceVersion));
        if (!marketPrice.isValidAt(now)) {
            throw PricingException.rejected("市场价格已过期: " + source + " v" + priceVersion);
        }
        if (quantity.compareTo(contract.remainingQuantity()) > 0) {
            throw PricingException.rejected("点价数量超过未点价数量，剩余: " + contract.remainingQuantity());
        }

        BigDecimal fixedPrice = marketPrice.getPrice().add(basis);
        PriceFixing fixing = fixingRepository.save(new PriceFixing(
                contract, externalFixingNo, quantity, marketPrice, basis, fixedPrice, now, requestHash));

        contract.addFixedQuantity(quantity);
        contractRepository.save(contract);

        BigDecimal adjustment = money(fixedPrice.subtract(contract.getProvisionalPrice()).multiply(quantity));
        int nextSeq = settlementRepository.findByContractIdOrderBySeqAsc(contractId).size() + 1;
        settlementRepository.save(new SettlementEntry(contract, nextSeq, SettlementEntryType.ADJUSTMENT,
                quantity, fixedPrice, adjustment, fixing.getId(), now));
        return fixing;
    }

    @Transactional
    public SettlementEntry finalizeSettlement(Long contractId) {
        Contract contract = contractRepository.findByIdForUpdate(contractId)
                .orElseThrow(() -> PricingException.notFound("合同不存在: " + contractId));

        var existingFinal = settlementRepository.findByContractIdAndType(contractId, SettlementEntryType.FINAL);
        if (existingFinal.isPresent()) {
            return existingFinal.get();
        }
        if (contract.remainingQuantity().signum() != 0) {
            throw PricingException.rejected("存在未点价数量，不能生成最终结算，剩余: "
                    + contract.remainingQuantity());
        }

        List<SettlementEntry> entries = settlementRepository.findByContractIdOrderBySeqAsc(contractId);
        BigDecimal total = entries.stream()
                .map(SettlementEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        SettlementEntry finalEntry = settlementRepository.save(new SettlementEntry(
                contract, entries.size() + 1, SettlementEntryType.FINAL,
                contract.getQuantity(), weightedAveragePrice(contract), money(total), null, now()));
        contract.markFinalSettled();
        contractRepository.save(contract);
        return finalEntry;
    }

    @Transactional(readOnly = true)
    public ContractView getContractView(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> PricingException.notFound("合同不存在: " + contractId));
        List<PriceFixing> fixings = fixingRepository.findByContractIdOrderByFixedAtAscIdAsc(contractId);
        List<SettlementEntry> entries = settlementRepository.findByContractIdOrderBySeqAsc(contractId);

        BigDecimal provisionalAmount = entries.stream()
                .filter(e -> e.getType() == SettlementEntryType.PROVISIONAL)
                .map(SettlementEntry::getAmount).findFirst().orElse(BigDecimal.ZERO);
        BigDecimal cumulativeAdjustment = entries.stream()
                .filter(e -> e.getType() == SettlementEntryType.ADJUSTMENT)
                .map(SettlementEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal finalAmount = entries.stream()
                .filter(e -> e.getType() == SettlementEntryType.FINAL)
                .map(SettlementEntry::getAmount).findFirst().orElse(null);

        List<ContractView.FixingSnapshot> snapshots = fixings.stream()
                .map(f -> new ContractView.FixingSnapshot(
                        f.getExternalFixingNo(), f.getQuantity(),
                        f.getMarketPrice().getSource(), f.getMarketPrice().getPriceVersion(),
                        f.getMarketPrice().getPrice(), f.getBasis(), f.getFixedPrice(), f.getFixedAt()))
                .toList();

        return new ContractView(contract.getId(), contract.getCommodity(), contract.getDirection(),
                contract.getCurrency(), contract.getStatus(), contract.getQuantity(),
                contract.remainingQuantity(), contract.getProvisionalPrice(), provisionalAmount,
                cumulativeAdjustment, finalAmount, snapshots);
    }

    private BigDecimal weightedAveragePrice(Contract contract) {
        List<PriceFixing> fixings = fixingRepository.findByContractIdOrderByFixedAtAscIdAsc(contract.getId());
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalValue = BigDecimal.ZERO;
        for (PriceFixing f : fixings) {
            totalQty = totalQty.add(f.getQuantity());
            totalValue = totalValue.add(f.getFixedPrice().multiply(f.getQuantity()));
        }
        if (totalQty.signum() == 0) {
            return contract.getProvisionalPrice();
        }
        return totalValue.divide(totalQty, 6, RoundingMode.HALF_UP);
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String hash(String externalFixingNo, BigDecimal quantity, String source,
                               long priceVersion, BigDecimal basis) {
        String material = externalFixingNo + "|" + quantity.stripTrailingZeros().toPlainString()
                + "|" + source + "|" + priceVersion + "|" + basis.stripTrailingZeros().toPlainString();
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
