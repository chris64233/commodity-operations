package com.chris64233.commodityoperations.dto;

import com.chris64233.commodityoperations.domain.PricingOrder;

import java.math.BigDecimal;
import java.time.Instant;

public record PricingSnapshotView(
        Long id,
        String externalPricingNo,
        BigDecimal quantity,
        String priceSource,
        String priceVersion,
        BigDecimal marketPrice,
        BigDecimal premiumDiscount,
        BigDecimal fixedPrice,
        BigDecimal adjustmentAmount,
        Instant pricedAt) {

    public static PricingSnapshotView from(PricingOrder order) {
        return new PricingSnapshotView(order.getId(), order.getExternalPricingNo(),
                order.getQuantity(), order.getPriceSource(), order.getPriceVersion(),
                order.getMarketPrice(), order.getPremiumDiscount(), order.getFixedPrice(),
                order.getAdjustmentAmount(), order.getPricedAt());
    }
}
