package com.chris64233.commodityoperations.repository;

import com.chris64233.commodityoperations.model.ClosureRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClosureRecordRepository extends JpaRepository<ClosureRecord, Long> {

    List<ClosureRecord> findByContractIdOrderByIdAsc(Long contractId);
}
