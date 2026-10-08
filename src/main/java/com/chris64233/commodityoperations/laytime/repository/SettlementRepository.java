package com.chris64233.commodityoperations.laytime.repository;

import com.chris64233.commodityoperations.laytime.domain.Settlement;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    List<Settlement> findByVoyage_IdOrderByVersionNoAsc(Long voyageId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Settlement s where s.voyage.id = :voyageId and s.current = true")
    Optional<Settlement> findCurrentByVoyageIdForUpdate(@Param("voyageId") Long voyageId);
}
