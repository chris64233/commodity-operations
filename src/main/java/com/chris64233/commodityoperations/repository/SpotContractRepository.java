package com.chris64233.commodityoperations.repository;

import com.chris64233.commodityoperations.model.SpotContract;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SpotContractRepository extends JpaRepository<SpotContract, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from SpotContract c where c.id = :id")
    Optional<SpotContract> findByIdForUpdate(@Param("id") Long id);
}
