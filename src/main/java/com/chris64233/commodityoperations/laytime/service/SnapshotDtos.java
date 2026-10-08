package com.chris64233.commodityoperations.laytime.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 结算单快照与查询视图。
 */
public final class SnapshotDtos {

    private SnapshotDtos() {
    }

    public record TimelinePoint(
            String externalEventNo,
            String eventType,
            Instant occurredAt) {
    }

    public record SuspensionView(
            Instant from,
            Instant to,
            long seconds,
            String pauseEventNos,
            String resumeEventNo) {
    }

    /**
     * 差额来源：以旧版为基准的各项数值变化（新值 - 旧值）。
     */
    public record DifferenceEntry(
            String item,
            BigDecimal oldValue,
            BigDecimal newValue,
            BigDecimal delta,
            String detail) {
    }

    public record SettlementView(
            Long id,
            String voyageNo,
            int versionNo,
            String status,
            boolean current,
            long pinnedEventVersion,
            long allowedLaytimeSeconds,
            BigDecimal demurrageRatePerHour,
            String currency,
            String stopRule,
            Instant startAt,
            Instant completeAt,
            long grossWorkSeconds,
            long deductedSeconds,
            long usedSeconds,
            long excessSeconds,
            BigDecimal demurrageAmount,
            List<String> eventRefs,
            List<TimelinePoint> timeline,
            List<SuspensionView> suspensions,
            Long basedOnSettlementId,
            String adjustmentReason,
            List<DifferenceEntry> differences,
            Instant createdAt,
            Instant confirmedAt) {
    }

    public record RawEventView(
            String externalEventNo,
            String eventType,
            Instant occurredAt,
            Instant receivedAt) {
    }

    public record VoyageDetailView(
            String voyageNo,
            long allowedLaytimeSeconds,
            BigDecimal demurrageRatePerHour,
            String currency,
            String stopRule,
            long eventVersion,
            List<RawEventView> rawEvents,
            List<SettlementView> settlements) {
    }
}
