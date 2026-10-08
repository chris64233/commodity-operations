package com.chris64233.commodityoperations.laytime.repo;

import com.chris64233.commodityoperations.laytime.domain.OperationEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OperationEventRepository extends JpaRepository<OperationEvent, String> {

    Optional<OperationEvent> findByVoyageIdAndExternalEventNo(String voyageId, String externalEventNo);

    List<OperationEvent> findByVoyageIdOrderByOccurredAtAscReceivedAtAsc(String voyageId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OperationEvent e where e.voyageId = :voyageId")
    List<OperationEvent> lockByVoyageId(@Param("voyageId") String voyageId);
}
