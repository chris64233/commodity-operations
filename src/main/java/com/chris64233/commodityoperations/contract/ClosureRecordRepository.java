package com.chris64233.commodityoperations.contract;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClosureRecordRepository extends JpaRepository<ClosureRecord, Long> {

    List<ClosureRecord> findByContractIdOrderByIdAsc(Long contractId);

    Optional<ClosureRecord> findFirstByContractIdAndStateOrderByIdDesc(Long contractId, ClosureRecord.State state);
}
