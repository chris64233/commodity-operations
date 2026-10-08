package com.chris64233.commodityoperations.dto;

import com.chris64233.commodityoperations.domain.Contract;
import com.chris64233.commodityoperations.domain.MoneyCalculations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public record ContractView(
        Long id,
        String contractNo,
        String commodity,
        String direction,
        String currency,
        String status,
        BigDecimal contractQty,
        BigDecimal pricedQty,
        BigDecimal unpricedQty,
        BigDecimal provisionalPrice,
        BigDecimal provisionalAmount,
        BigDecimal cumulativeAdjustment,
        BigDecimal finalAmount,
        Instant pricingWindowStart,
        Instant pricingWindowEnd,
        Set<String> allowedSources,
        List<PricingSnapshotView> pricings,
        List<SettlementVersionView> settlementVersions) {

    public static ContractView from(Contract contract,
                                    BigDecimal cumulativeAdjustment,
                                    List<PricingSnapshotView> pricings,
                                    List<SettlementVersionView> versions) {
        BigDecimal finalAmount = null;
        if (!versions.isEmpty() && "FINAL".equals(versions.get(versions.size() - 1).type())) {
            finalAmount = versions.get(versions.size() - 1).totalAmount();
        }
        BigDecimal contractQty = contract.getContractQty().setScale(
                MoneyCalculations.PRICE_SCALE, MoneyCalculations.ROUNDING);
        BigDecimal pricedQty = contract.getPricedQty().setScale(
                MoneyCalculations.PRICE_SCALE, MoneyCalculations.ROUNDING);
        BigDecimal unpricedQty = contract.unpricedQty().setScale(
                MoneyCalculations.PRICE_SCALE, MoneyCalculations.ROUNDING);
        BigDecimal provisionalPrice = contract.getProvisionalPrice().setScale(
                MoneyCalculations.PRICE_SCALE, MoneyCalculations.ROUNDING);
        return new ContractView(
                contract.getId(),
                contract.getContractNo(),
                contract.getCommodity(),
                contract.getDirection().name(),
                contract.getCurrency(),
                contract.getStatus().name(),
                contractQty,
                pricedQty,
                unpricedQty,
                provisionalPrice,
                contract.getProvisionalAmount(),
                MoneyCalculations.normalizeAmount(cumulativeAdjustment),
                finalAmount,
                contract.getPricingWindowStart(),
                contract.getPricingWindowEnd(),
                contract.getAllowedSources(),
                pricings,
                versions);
    }
}
