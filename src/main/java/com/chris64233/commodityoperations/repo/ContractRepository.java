package com.chris64233.commodityoperations.repo;

import com.chris64233.commodityoperations.domain.Contract;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.Optional;

public interface ContractRepository extends JpaRepository<Contract, Long> {

    boolean existsByContractNo(String contractNo);

    Optional<Contract> findByContractNo(String contractNo);

    /**
     * 行级悲观锁串行化同一合同上的并发点价/结算，保证只消耗未点价数量。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select c from Contract c where c.id = :id")
    Optional<Contract> findByIdForUpdate(Long id);
}
