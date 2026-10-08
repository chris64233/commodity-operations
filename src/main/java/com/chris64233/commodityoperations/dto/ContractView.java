package com.chris64233.commodityoperations.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContractView(
        Long id,
        String contractCode,
        String counterparty,
        BigDecimal allowedQuantity,
        BigDecimal committedQuantity,
        BigDecimal remainingQuantity,
        LocalDate windowStart,
        LocalDate windowEnd) {
}
