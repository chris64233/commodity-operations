package com.chris64233.commodityoperations.dto;

import com.chris64233.commodityoperations.model.ContractStatus;
import com.chris64233.commodityoperations.model.DeliveryType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ContractViews {

    private ContractViews() {
    }

    public record DeliveryView(
            Long id,
            String externalRef,
            BigDecimal quantity,
            LocalDate deliveryDate,
            DeliveryType type,
            Long reversalOfId) {
    }

    public record ClosureView(
            Long id,
            BigDecimal cumulativeQuantity,
            Instant closedAt,
            boolean superseded) {
    }

    public record ContractView(
            Long id,
            String commodity,
            String unit,
            BigDecimal baseQuantity,
            BigDecimal shortTolerancePercent,
            BigDecimal overTolerancePercent,
            BigDecimal minAcceptableQuantity,
            BigDecimal maxAcceptableQuantity,
            BigDecimal cumulativeQuantity,
            BigDecimal remainingReceivableQuantity,
            LocalDate deliveryDeadline,
            ContractStatus status,
            List<DeliveryView> deliveries,
            List<ClosureView> closures) {
    }
}
