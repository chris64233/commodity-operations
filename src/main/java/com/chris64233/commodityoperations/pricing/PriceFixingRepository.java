package com.chris64233.commodityoperations.pricing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PriceFixingRepository extends JpaRepository<PriceFixing, Long> {

    Optional<PriceFixing> findByContractIdAndExternalFixingNo(Long contractId, String externalFixingNo);

    List<PriceFixing> findByContractIdOrderByFixedAtAscIdAsc(Long contractId);
}
