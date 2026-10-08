package com.chris64233.commodityoperations.vesselnomination;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface TerminalDailyCapacityRepository extends JpaRepository<TerminalDailyCapacity, Long> {

    Optional<TerminalDailyCapacity> findByCapacityDate(LocalDate capacityDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from TerminalDailyCapacity c where c.capacityDate = :date")
    Optional<TerminalDailyCapacity> findByCapacityDateForUpdate(@Param("date") LocalDate date);
}
