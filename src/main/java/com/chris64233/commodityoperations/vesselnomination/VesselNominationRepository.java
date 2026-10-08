package com.chris64233.commodityoperations.vesselnomination;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VesselNominationRepository extends JpaRepository<VesselNomination, Long> {

    @Query("select coalesce(sum(n.plannedQuantity), 0) from VesselNomination n "
            + "where n.contract.id = :contractId and n.status in :statuses")
    long sumPlannedQuantityByContractAndStatusIn(@Param("contractId") Long contractId,
                                                 @Param("statuses") Collection<NominationStatus> statuses);

    Optional<VesselNomination> findFirstByVesselNameAndStatus(String vesselName, NominationStatus status);

    List<VesselNomination> findByVesselNameOrderByIdDesc(String vesselName);
}
