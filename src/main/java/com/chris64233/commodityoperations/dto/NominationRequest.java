package com.chris64233.commodityoperations.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NominationRequest(
        @NotBlank String contractCode,
        @NotBlank String vesselCode,
        String vesselName,
        @NotNull LocalDateTime estimatedArrival,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal plannedQuantity,
        String requestId) {
}
