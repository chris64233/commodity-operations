package com.chris64233.commodityoperations.laytime.service;

import com.chris64233.commodityoperations.laytime.domain.*;
import com.chris64233.commodityoperations.laytime.dto.Requests.CreateVoyageRequest;
import com.chris64233.commodityoperations.laytime.dto.Requests.IngestEventRequest;
import com.chris64233.commodityoperations.laytime.dto.Responses.*;
import com.chris64233.commodityoperations.laytime.engine.*;
import com.chris64233.commodityoperations.laytime.repo.OperationEventRepository;
import com.chris64233.commodityoperations.laytime.repo.SettlementRepository;
import com.chris64233.commodityoperations.laytime.repo.VoyageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 装卸时间与滞期费结算核心服务。
 *
 * <p>所有写操作先对航次行加悲观锁串行化，保证「重算与结算确认同时发生」时，
 * 只有基于最新完整事件版本的结果成为当前版本；计算失败抛出异常使整笔事务回滚，
 * 不会产生半张结算单，也不会改动已确认金额。</p>
 */
@Service
public class LaytimeService {

    static final String INITIAL_REASON = "INITIAL";

    private final VoyageRepository voyageRepository;
    private final OperationEventRepository eventRepository;
    private final SettlementRepository settlementRepository;
    private final TimelineNormalizer normalizer;
    private final LaytimeCalculator calculator;

    public LaytimeService(VoyageRepository voyageRepository,
                          OperationEventRepository eventRepository,
                          SettlementRepository settlementRepository,
                          TimelineNormalizer normalizer,
                          LaytimeCalculator calculator) {
        this.voyageRepository = voyageRepository;
        this.eventRepository = eventRepository;
        this.settlementRepository = settlementRepository;
        this.normalizer = normalizer;
        this.calculator = calculator;
    }

    @Transactional
    public VoyageResponse createVoyage(CreateVoyageRequest req) {
        voyageRepository.findByVoyageCode(req.voyageCode()).ifPresent(v -> {
            throw ApiException.conflict("航次号已存在: " + req.voyageCode());
        });
        Voyage voyage = new Voyage(req.voyageCode(), req.vesselName(),
                java.time.Duration.ofSeconds(req.allowedLaytimeSeconds()),
                req.demurrageRatePerDay(), req.currency(), req.countingBasis());
        return toVoyageResponse(voyageRepository.save(voyage));
    }

    /**
     * 登记作业事件。同号同内容重放幂等返回；同号内容变化返回冲突。
     */
    @Transactional
    public EventResponse ingestEvent(String voyageCode, IngestEventRequest req) {
        Voyage voyage = lockVoyage(voyageCode);
        Instant receivedAt = req.receivedAt() != null ? req.receivedAt() : Instant.now();

        var existing = eventRepository
                .findByVoyageIdAndExternalEventNo(voyage.getId(), req.externalEventNo());
        if (existing.isPresent()) {
            OperationEvent old = existing.get();
            if (old.getType() == req.type() && old.getOccurredAt().equals(req.occurredAt())) {
                return new EventResponse(old.getId(), old.getExternalEventNo(), old.getType(),
                        old.getOccurredAt(), old.getReceivedAt(), true);
            }
            throw ApiException.conflict(String.format(
                    "外部事件号 %s 已存在但内容不同：已登记 type=%s, occurredAt=%s；"
                            + "本次提交 type=%s, occurredAt=%s",
                    req.externalEventNo(), old.getType(), old.getOccurredAt(),
                    req.type(), req.occurredAt()));
        }
        OperationEvent saved = eventRepository.save(new OperationEvent(
                voyage.getId(), req.externalEventNo(), req.type(), req.occurredAt(), receivedAt));
        return new EventResponse(saved.getId(), saved.getExternalEventNo(), saved.getType(),
                saved.getOccurredAt(), saved.getReceivedAt(), false);
    }

    /**
     * 基于当前全部事件生成结算版本。
     *
     * <p>当前没有结算：生成首版 DRAFT。当前为 DRAFT：以最新事件重算并替换当前版本
     *（旧 DRAFT 标记 SUPERSEDED）。当前已 CONFIRMED：生成新的 DRAFT 调整版本，
     * 已确认版本金额与原因原样保留，新版本记录差额（{@code adjustmentDelta}）与调整原因。</p>
     */
    @Transactional
    public SettlementResponse generateSettlement(String voyageCode, String reason) {
        Voyage voyage = lockVoyage(voyageCode);
        eventRepository.lockByVoyageId(voyage.getId());
        List<OperationEvent> events =
                eventRepository.findByVoyageIdOrderByOccurredAtAscReceivedAtAsc(voyage.getId());

        NormalizedTimeline timeline = normalizer.normalize(events);
        LaytimeResult result = calculator.calculate(voyage, timeline);

        Settlement current = settlementRepository.lockCurrent(voyage.getId()).orElse(null);

        String eventHash = EventVersionHasher.hashEvents(events);
        if (current != null && current.getEventVersionHash().equals(eventHash)
                && current.getStatus() == SettlementStatus.DRAFT) {
            return toSettlementResponse(current, voyage.getCurrency());
        }

        int nextVersion = current == null ? 1 : current.getVersionNo() + 1;
        BigDecimal previousAmount = current == null ? BigDecimal.ZERO : current.getDemurrageAmount();
        BigDecimal delta = result.demurrage().subtract(previousAmount);
        String adjReason = current == null
                ? INITIAL_REASON
                : buildAdjustmentReason(reason, current, events);
        String previousId = current == null ? null : current.getId();

        if (current != null) {
            current.markSuperseded();
            settlementRepository.save(current);
        }

        Settlement settlement = new Settlement(
                voyage.getId(), nextVersion,
                EventVersionHasher.contractSnapshot(voyage), eventHash, events.size(),
                result.countingStart(), result.completeAt(),
                result.usedLaytime(), result.suspended(), result.allowedLaytime(), result.excess(),
                result.demurrage(), delta, adjReason, previousId, true);
        return toSettlementResponse(settlementRepository.save(settlement), voyage.getCurrency());
    }

    private String buildAdjustmentReason(String reason, Settlement current, List<OperationEvent> events) {
        String prefix = current.getStatus() == SettlementStatus.CONFIRMED
                ? "CONFIRMED_V" + current.getVersionNo() + "_ADJUSTMENT"
                : "DRAFT_RECALC";
        String late = events.stream()
                .filter(e -> e.getReceivedAt().isAfter(current.getCreatedAt()))
                .map(OperationEvent::getExternalEventNo)
                .reduce((a, b) -> a + "," + b)
                .orElse("none");
        String detail = (reason == null || reason.isBlank()) ? "late events: " + late : reason;
        return prefix + ": " + detail;
    }

    /**
     * 确认当前结算版本。只有当前版本可确认；确认后金额冻结。
     */
    @Transactional
    public SettlementResponse confirmSettlement(String voyageCode, String settlementId) {
        Voyage voyage = lockVoyage(voyageCode);
        Settlement current = settlementRepository.lockCurrent(voyage.getId())
                .orElseThrow(() -> ApiException.notFound("航次尚无结算版本: " + voyageCode));
        if (settlementId != null && !settlementId.equals(current.getId())) {
            throw ApiException.conflict("只能确认当前最新版本（当前版本 id=" + current.getId() + "）");
        }
        if (current.getStatus() == SettlementStatus.CONFIRMED) {
            return toSettlementResponse(current, voyage.getCurrency());
        }
        current.confirm();
        return toSettlementResponse(settlementRepository.save(current), voyage.getCurrency());
    }

    @Transactional(readOnly = true)
    public VoyageDetailResponse getVoyageDetail(String voyageCode) {
        Voyage voyage = voyageRepository.findByVoyageCode(voyageCode)
                .orElseThrow(() -> ApiException.notFound("航次不存在: " + voyageCode));
        List<OperationEvent> events =
                eventRepository.findByVoyageIdOrderByOccurredAtAscReceivedAtAsc(voyage.getId());
        List<EventResponse> rawEvents = events.stream()
                .map(e -> new EventResponse(e.getId(), e.getExternalEventNo(), e.getType(),
                        e.getOccurredAt(), e.getReceivedAt(), false))
                .toList();

        TimelineView timelineView = null;
        try {
            NormalizedTimeline timeline = normalizer.normalize(events);
            LaytimeResult result = calculator.calculate(voyage, timeline);
            timelineView = new TimelineView(
                    timeline.berthAt(), timeline.startAt(), timeline.completeAt(),
                    result.countingStart(), result.grossSpan().getSeconds(),
                    result.suspended().getSeconds(), result.usedLaytime().getSeconds(),
                    timeline.suspensions().stream()
                            .map(s -> new SuspensionView(s.from(), s.to(), s.duration().getSeconds()))
                            .toList(),
                    timeline.orderedEvents().stream()
                            .map(e -> new TimelineEventView(e.getExternalEventNo(), e.getType(),
                                    e.getOccurredAt(), e.getReceivedAt()))
                            .toList());
        } catch (TimelineException ex) {
            // 时间线不完整时仍可查询原始事件与历史结算；时间线部分留空。
        }

        List<Settlement> settlements = settlementRepository.findByVoyageIdOrderByVersionNoAsc(voyage.getId());
        List<SettlementResponse> settlementViews =
                settlements.stream().map(s -> toSettlementResponse(s, voyage.getCurrency())).toList();
        SettlementResponse currentView = settlements.stream()
                .filter(Settlement::isCurrent)
                .findFirst()
                .map(s -> toSettlementResponse(s, voyage.getCurrency()))
                .orElse(null);
        return new VoyageDetailResponse(toVoyageResponse(voyage), rawEvents, timelineView,
                settlementViews, currentView);
    }

    private Voyage lockVoyage(String voyageCode) {
        Voyage voyage = voyageRepository.findByVoyageCode(voyageCode)
                .orElseThrow(() -> ApiException.notFound("航次不存在: " + voyageCode));
        return voyageRepository.lockById(voyage.getId())
                .orElseThrow(() -> ApiException.notFound("航次不存在: " + voyageCode));
    }

    private VoyageResponse toVoyageResponse(Voyage v) {
        return new VoyageResponse(v.getId(), v.getVoyageCode(), v.getVesselName(),
                v.getAllowedLaytimeSeconds(), v.getDemurrageRatePerDay(),
                v.getCurrency(), v.getCountingBasis());
    }

    private SettlementResponse toSettlementResponse(Settlement s, String currency) {
        return new SettlementResponse(s.getId(), s.getVersionNo(), s.getStatus(), s.isCurrent(),
                s.getContractSnapshot(), s.getEventVersionHash(), s.getEventCount(),
                s.getCountingStartedAt(), s.getCompletedAt(),
                s.getUsedLaytimeSeconds(), s.getAllowedLaytimeSeconds(),
                s.getSuspendedSeconds(), s.getExcessSeconds(),
                s.getDemurrageAmount(), currency,
                s.getAdjustmentDelta(), s.getAdjustmentReason(), s.getPreviousVersionId(),
                s.getCreatedAt(), s.getConfirmedAt());
    }
}
