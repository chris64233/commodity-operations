package com.chris64233.commodityoperations.laytime.repository;

import com.chris64233.commodityoperations.laytime.domain.OperationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OperationEventRepository extends JpaRepository<OperationEvent, Long> {

    Optional<OperationEvent> findByVoyageIdAndExternalEventNo(Long voyageId, String externalEventNo);

    List<OperationEvent> findByVoyage_IdOrderByOccurredAtAscIdAsc(Long voyageId);
}
