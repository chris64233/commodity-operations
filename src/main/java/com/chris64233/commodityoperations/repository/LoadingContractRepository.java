package com.chris64233.commodityoperations.repository;

import java.util.Optional;

import com.chris64233.commodityoperations.domain.LoadingContract;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoadingContractRepository extends JpaRepository<LoadingContract, Long> {

    Optional<LoadingContract> findByContractCode(String contractCode);

    boolean existsByContractCode(String contractCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from LoadingContract c where c.id = :id")
    java.util.Optional<LoadingContract> findByIdForUpdate(@Param("id") Long id);
}
