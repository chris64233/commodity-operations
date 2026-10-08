package com.chris64233.commodityoperations.repository;

import com.chris64233.commodityoperations.model.DeliveryRecord;
import com.chris64233.commodityoperations.model.DeliveryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface DeliveryRecordRepository extends JpaRepository<DeliveryRecord, Long> {

    Optional<DeliveryRecord> findByContractIdAndExternalRef(Long contractId, String externalRef);

    List<DeliveryRecord> findByContractIdOrderByIdAsc(Long contractId);

    @Query("select coalesce(sum(d.quantity), 0) from DeliveryRecord d where d.contractId = :contractId")
    BigDecimal sumQuantityByContractId(@Param("contractId") Long contractId);

    @Query("select coalesce(sum(-d.quantity), 0) from DeliveryRecord d "
            + "where d.reversalOfId = :deliveryId and d.type = :type")
    BigDecimal sumReversedByDeliveryId(@Param("deliveryId") Long deliveryId,
                                       @Param("type") DeliveryType type);
}
