package com.chris64233.commodityoperations.laytime.dto;

import com.chris64233.commodityoperations.laytime.domain.CountingBasis;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;

public final class Requests {

    private Requests() {
    }

    public record CreateVoyageRequest(
            @NotBlank String voyageCode,
            String vesselName,
            @NotNull @Positive Long allowedLaytimeSeconds,
            @NotNull @DecimalMin("0.0") BigDecimal demurrageRatePerDay,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
            @NotNull CountingBasis countingBasis) {
    }

    public record IngestEventRequest(
            @NotBlank String externalEventNo,
            @NotNull com.chris64233.commodityoperations.laytime.domain.EventType type,
            @NotNull Instant occurredAt,
            Instant receivedAt) {
    }

    public record RecalculateRequest(
            String reason) {
    }
}
