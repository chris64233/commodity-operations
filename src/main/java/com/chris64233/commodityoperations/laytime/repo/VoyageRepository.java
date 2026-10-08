package com.chris64233.commodityoperations.laytime.repo;

import com.chris64233.commodityoperations.laytime.domain.Voyage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VoyageRepository extends JpaRepository<Voyage, String> {

    Optional<Voyage> findByVoyageCode(String voyageCode);

    /** 悲观行锁：登记事件与生成/确认结算串行化，保证并发下只有最新完整版本成为当前版本。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Voyage v where v.id = :id")
    Optional<Voyage> lockById(@Param("id") String id);
}
