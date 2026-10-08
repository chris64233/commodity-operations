package com.chris64233.commodityoperations.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

public record RegisterContractRequest(
        @NotBlank String contractNo,
        @NotBlank String commodity,
        @NotNull com.chris64233.commodityoperations.domain.ContractDirection direction,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
        @NotBlank @Size(max = 8) String currency,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal provisionalPrice,
        @NotNull Instant pricingWindowStart,
        @NotNull Instant pricingWindowEnd,
        @NotEmpty Set<@NotBlank String> allowedSources) {
}
