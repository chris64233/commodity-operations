package com.chris64233.commodityoperations.laytime.dto;

import com.chris64233.commodityoperations.laytime.domain.CountingBasis;
import com.chris64233.commodityoperations.laytime.domain.EventType;
import com.chris64233.commodityoperations.laytime.domain.SettlementStatus;

import java.math.BigDecimal;
import java.time.Instant;

public final class Responses {

    private Responses() {
    }

    public record VoyageResponse(String id, String voyageCode, String vesselName,
                                 long allowedLaytimeSeconds, BigDecimal demurrageRatePerDay,
                                 String currency, CountingBasis countingBasis) {
    }

    public record EventResponse(String id, String externalEventNo, EventType type,
                                Instant occurredAt, Instant receivedAt, boolean replayed) {
    }

    public record TimelineEventView(String externalEventNo, EventType type,
                                    Instant occurredAt, Instant receivedAt) {
    }

    public record SuspensionView(Instant from, Instant to, long seconds) {
    }

    public record TimelineView(Instant berthAt, Instant startAt, Instant completeAt,
                               Instant countingStart, long grossSpanSeconds,
                               long suspendedSeconds, long usedLaytimeSeconds,
                               java.util.List<SuspensionView> suspensions,
                               java.util.List<TimelineEventView> events) {
    }

    public record SettlementResponse(String id, int versionNo, SettlementStatus status,
                                     boolean current, String contractSnapshot,
                                     String eventVersionHash, int eventCount,
                                     Instant countingStartedAt, Instant completedAt,
                                     long usedLaytimeSeconds, long allowedLaytimeSeconds,
                                     long suspendedSeconds, long excessSeconds,
                                     BigDecimal demurrageAmount, String currency,
                                     BigDecimal adjustmentDelta, String adjustmentReason,
                                     String previousVersionId,
                                     Instant createdAt, Instant confirmedAt) {
    }

    public record VoyageDetailResponse(VoyageResponse voyage,
                                       java.util.List<EventResponse> rawEvents,
                                       TimelineView timeline,
                                       java.util.List<SettlementResponse> settlements,
                                       SettlementResponse currentSettlement) {
    }
}
