package com.chris64233.commodityoperations.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record PublishMarketPriceRequest(
        @NotBlank String source,
        @NotBlank String priceVersion,
        @NotBlank String commodity,
        @NotBlank String currency,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal price,
        @NotNull Instant validFrom,
        @NotNull Instant validTo) {
}
