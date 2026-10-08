package com.chris64233.commodityoperations.vesselnomination;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LoadingContractRepository extends JpaRepository<LoadingContract, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from LoadingContract c where c.id = :id")
    Optional<LoadingContract> findByIdForUpdate(@Param("id") Long id);
}
