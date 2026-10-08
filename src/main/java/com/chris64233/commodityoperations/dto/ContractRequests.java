package com.chris64233.commodityoperations.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class ContractRequests {

    private ContractRequests() {
    }

    public record CreateContractRequest(
            @NotBlank String commodity,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal baseQuantity,
            @NotNull @DecimalMin(value = "0") BigDecimal shortTolerancePercent,
            @NotNull @DecimalMin(value = "0") BigDecimal overTolerancePercent,
            @NotNull LocalDate deliveryDeadline,
            @NotBlank String unit) {
    }

    public record RegisterDeliveryRequest(
            @NotBlank String externalRef,
            @NotBlank String commodity,
            @NotBlank String unit,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
            @NotNull LocalDate deliveryDate) {
    }

    public record ReverseDeliveryRequest(
            @NotBlank String externalRef,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
            @NotNull LocalDate reversalDate) {
    }
}
