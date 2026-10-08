package com.chris64233.commodityoperations.contract;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    Optional<Delivery> findByContractIdAndExternalRef(Long contractId, String externalRef);

    List<Delivery> findByContractIdOrderByIdAsc(Long contractId);

    boolean existsByReversalOfId(Long deliveryId);

    @Query("select coalesce(sum(d.quantity), 0) from Delivery d where d.contractId = :contractId")
    BigDecimal sumQuantityByContractId(@Param("contractId") Long contractId);
}
