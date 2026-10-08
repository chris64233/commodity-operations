package com.chris64233.commodityoperations.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.chris64233.commodityoperations.domain.VesselNomination;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VesselNominationRepository extends JpaRepository<VesselNomination, Long> {

    Optional<VesselNomination> findByRequestId(String requestId);

    List<VesselNomination> findByContractIdOrderByIdAsc(Long contractId);

    List<VesselNomination> findByVesselCodeAndActiveFlagTrueOrderByIdAsc(String vesselCode);

    @Query("select coalesce(sum(n.plannedQuantity), 0) from VesselNomination n "
            + "where n.contractId = :contractId and n.activeFlag = true")
    BigDecimal sumActiveQuantity(@Param("contractId") Long contractId);

    @Query("select count(n) from VesselNomination n "
            + "where n.vesselCode = :vesselCode and n.activeFlag = true")
    long countActiveByVessel(@Param("vesselCode") String vesselCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from VesselNomination n where n.id = :id")
    Optional<VesselNomination> findByIdForUpdate(@Param("id") Long id);
}
