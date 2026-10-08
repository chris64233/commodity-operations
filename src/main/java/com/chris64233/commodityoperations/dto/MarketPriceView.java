package com.chris64233.commodityoperations.dto;

import com.chris64233.commodityoperations.domain.MarketPrice;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketPriceView(
        Long id,
        String source,
        String priceVersion,
        String commodity,
        String currency,
        BigDecimal price,
        Instant validFrom,
        Instant validTo) {

    public static MarketPriceView from(MarketPrice mp) {
        return new MarketPriceView(mp.getId(), mp.getSource(), mp.getPriceVersion(),
                mp.getCommodity(), mp.getCurrency(), mp.getPrice(),
                mp.getValidFrom(), mp.getValidTo());
    }
}
