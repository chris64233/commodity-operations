package com.chris64233.commodityoperations.dto;

import com.chris64233.commodityoperations.domain.SettlementVersion;

import java.math.BigDecimal;
import java.time.Instant;

public record SettlementVersionView(
        int versionNo,
        String type,
        Long pricingOrderId,
        String externalPricingNo,
        BigDecimal deltaAmount,
        BigDecimal cumulativeAdjustment,
        BigDecimal totalAmount,
        Instant createdAt) {

    public static SettlementVersionView from(SettlementVersion v) {
        String externalNo = v.getPricingOrder() == null ? null
                : v.getPricingOrder().getExternalPricingNo();
        Long pricingId = v.getPricingOrder() == null ? null : v.getPricingOrder().getId();
        return new SettlementVersionView(v.getVersionNo(), v.getType().name(), pricingId,
                externalNo, v.getDeltaAmount(), v.getCumulativeAdjustment(),
                v.getTotalAmount(), v.getCreatedAt());
    }
}
