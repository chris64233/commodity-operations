package com.chris64233.commodityoperations.laytime.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record VoyageRequest(
        @NotBlank(message = "航次号不能为空")
        String voyageNo,

        @NotNull(message = "允许装卸时间不能为空")
        @Pattern(regexp = "PT.*", message = "允许装卸时间必须为 ISO-8601 时长，例如 PT72H")
        String allowedLaytime,

        @NotNull(message = "滞期费率不能为空")
        @DecimalMin(value = "0.0", message = "滞期费率不能为负")
        BigDecimal demurrageRatePerHour,

        @NotBlank(message = "计价币种不能为空")
        String currency,

        @Pattern(regexp = "DEDUCT_PAUSE_INTERVALS|CONTINUOUS",
                message = "停算规则必须为 DEDUCT_PAUSE_INTERVALS 或 CONTINUOUS")
        String stopRule) {

    public String resolvedStopRule() {
        return stopRule == null || stopRule.isBlank() ? "DEDUCT_PAUSE_INTERVALS" : stopRule;
    }
}
