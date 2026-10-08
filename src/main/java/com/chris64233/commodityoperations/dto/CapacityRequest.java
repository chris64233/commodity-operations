package com.chris64233.commodityoperations.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record CapacityRequest(
        @NotNull LocalDate date,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal availableCapacity) {
}
