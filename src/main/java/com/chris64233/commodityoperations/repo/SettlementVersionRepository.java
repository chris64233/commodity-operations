package com.chris64233.commodityoperations.repo;

import com.chris64233.commodityoperations.domain.Contract;
import com.chris64233.commodityoperations.domain.SettlementVersion;
import com.chris64233.commodityoperations.domain.SettlementVersionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SettlementVersionRepository extends JpaRepository<SettlementVersion, Long> {

    List<SettlementVersion> findByContractOrderByVersionNoAsc(Contract contract);

    Optional<SettlementVersion> findByContractAndType(Contract contract, SettlementVersionType type);
}
