package com.chris64233.commodityoperations.pricing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SettlementEntryRepository extends JpaRepository<SettlementEntry, Long> {

    List<SettlementEntry> findByContractIdOrderBySeqAsc(Long contractId);

    Optional<SettlementEntry> findByContractIdAndType(Long contractId, SettlementEntryType type);
}
