package com.chris64233.commodityoperations.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record SubmitPricingRequest(
        @NotBlank String externalPricingNo,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
        @NotBlank String priceSource,
        @NotBlank String priceVersion,
        @NotNull BigDecimal premiumDiscount,
        @NotNull Instant pricedAt) {
}
