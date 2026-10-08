package com.chris64233.commodityoperations.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.chris64233.commodityoperations.domain.NominationStatus;

public record NominationView(
        Long id,
        Long contractId,
        String contractCode,
        String vesselCode,
        String vesselName,
        LocalDateTime estimatedArrival,
        BigDecimal plannedQuantity,
        NominationStatus status,
        boolean active) {
}
