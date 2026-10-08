package com.chris64233.commodityoperations.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ContractRequest(
        @NotBlank String contractCode,
        String counterparty,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal allowedQuantity,
        @NotNull LocalDate windowStart,
        @NotNull LocalDate windowEnd) {
}
