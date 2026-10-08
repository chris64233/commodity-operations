package com.chris64233.commodityoperations.repo;

import com.chris64233.commodityoperations.domain.MarketPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MarketPriceRepository extends JpaRepository<MarketPrice, Long> {

    Optional<MarketPrice> findBySourceAndPriceVersion(String source, String priceVersion);

    boolean existsBySourceAndPriceVersion(String source, String priceVersion);
}
