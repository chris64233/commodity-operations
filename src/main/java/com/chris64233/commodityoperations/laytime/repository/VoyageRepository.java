package com.chris64233.commodityoperations.laytime.repository;

import com.chris64233.commodityoperations.laytime.domain.Voyage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VoyageRepository extends JpaRepository<Voyage, Long> {

    Optional<Voyage> findByVoyageNo(String voyageNo);

    boolean existsByVoyageNo(String voyageNo);
}
