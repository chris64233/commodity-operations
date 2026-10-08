package com.chris64233.commodityoperations.repository;

import java.util.Optional;

import com.chris64233.commodityoperations.domain.VesselGuard;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VesselGuardRepository extends JpaRepository<VesselGuard, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from VesselGuard g where g.vesselCode = :vesselCode")
    Optional<VesselGuard> findByCodeForUpdate(@Param("vesselCode") String vesselCode);
}
