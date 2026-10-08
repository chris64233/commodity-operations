package com.chris64233.commodityoperations.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CapacityView(
        LocalDate date,
        BigDecimal availableCapacity,
        BigDecimal reservedCapacity,
        BigDecimal remainingCapacity) {
}
