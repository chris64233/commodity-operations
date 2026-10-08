package com.chris64233.commodityoperations.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.chris64233.commodityoperations.domain.DailyCapacity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyCapacityRepository extends JpaRepository<DailyCapacity, Long> {

    Optional<DailyCapacity> findByDate(LocalDate date);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from DailyCapacity c where c.date = :date")
    Optional<DailyCapacity> findByDateForUpdate(@Param("date") LocalDate date);

    List<DailyCapacity> findAllByOrderByDateAsc();
}
