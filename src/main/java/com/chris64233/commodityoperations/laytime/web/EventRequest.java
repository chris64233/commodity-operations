package com.chris64233.commodityoperations.laytime.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record EventRequest(
        @NotBlank(message = "外部事件号不能为空")
        String externalEventNo,

        @NotBlank(message = "事件类型不能为空")
        String eventType,

        @NotNull(message = "事件发生时间不能为空")
        Instant occurredAt) {
}
