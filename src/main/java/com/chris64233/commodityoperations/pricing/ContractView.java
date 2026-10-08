package com.chris64233.commodityoperations.pricing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ContractView(
        Long contractId,
        String commodity,
        Direction direction,
        String currency,
        ContractStatus status,
        BigDecimal contractQuantity,
        BigDecimal remainingQuantity,
        BigDecimal provisionalPrice,
        BigDecimal provisionalAmount,
        BigDecimal cumulativeAdjustment,
        BigDecimal finalAmount,
        List<FixingSnapshot> fixings) {

    public record FixingSnapshot(
            String externalFixingNo,
            BigDecimal quantity,
            String source,
            long priceVersion,
            BigDecimal marketPrice,
            BigDecimal basis,
            BigDecimal fixedPrice,
            Instant fixedAt) {
    }
}
