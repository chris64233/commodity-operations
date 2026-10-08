package com.chris64233.commodityoperations.dto;

import java.time.LocalDateTime;

public record HistoryView(
        Long id,
        Long nominationId,
        String action,
        String detail,
        LocalDateTime eventTime) {
}
