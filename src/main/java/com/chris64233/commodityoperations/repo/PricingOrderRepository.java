package com.chris64233.commodityoperations.repo;

import com.chris64233.commodityoperations.domain.PricingOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PricingOrderRepository extends JpaRepository<PricingOrder, Long> {

    Optional<PricingOrder> findByExternalPricingNo(String externalPricingNo);
}
