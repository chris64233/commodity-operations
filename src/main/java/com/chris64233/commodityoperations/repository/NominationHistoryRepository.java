package com.chris64233.commodityoperations.repository;

import java.util.List;

import com.chris64233.commodityoperations.domain.NominationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NominationHistoryRepository extends JpaRepository<NominationHistory, Long> {

    List<NominationHistory> findByNominationIdOrderByEventTimeAscIdAsc(Long nominationId);
}
