package com.chris64233.commodityoperations.vesselnomination;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NominationEventRepository extends JpaRepository<NominationEvent, Long> {

    List<NominationEvent> findByNominationIdOrderByOccurredAtAscIdAsc(Long nominationId);
}
