package com.chris64233.commodityoperations.pricing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MarketPriceRepository extends JpaRepository<MarketPrice, Long> {

    Optional<MarketPrice> findBySourceAndCommodityAndCurrencyAndPriceVersion(
            String source, String commodity, String currency, long priceVersion);
}
