package com.chris64233.commodityoperations.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;

public record RescheduleRequest(@NotNull LocalDateTime newEstimatedArrival) {
}
