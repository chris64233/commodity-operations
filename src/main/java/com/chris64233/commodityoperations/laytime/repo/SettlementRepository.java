package com.chris64233.commodityoperations.laytime.repo;

import com.chris64233.commodityoperations.laytime.domain.Settlement;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, String> {

    List<Settlement> findByVoyageIdOrderByVersionNoAsc(String voyageId);

    Optional<Settlement> findByVoyageIdAndCurrentTrue(String voyageId);

    Optional<Settlement> findByIdAndVoyageId(String id, String voyageId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Settlement s where s.voyageId = :voyageId and s.current = true")
    Optional<Settlement> lockCurrent(@Param("voyageId") String voyageId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Settlement s where s.voyageId = :voyageId")
    List<Settlement> lockByVoyageId(@Param("voyageId") String voyageId);
}
