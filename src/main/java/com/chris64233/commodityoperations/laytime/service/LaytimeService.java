package com.chris64233.commodityoperations.laytime.service;

import com.chris64233.commodityoperations.laytime.calc.LaytimeCalculator;
import com.chris64233.commodityoperations.laytime.calc.NormalizedTimeline;
import com.chris64233.commodityoperations.laytime.calc.RawEvent;
import com.chris64233.commodityoperations.laytime.domain.EventType;
import com.chris64233.commodityoperations.laytime.domain.OperationEvent;
import com.chris64233.commodityoperations.laytime.domain.Settlement;
import com.chris64233.commodityoperations.laytime.domain.SettlementStatus;
import com.chris64233.commodityoperations.laytime.domain.StopRule;
import com.chris64233.commodityoperations.laytime.domain.Voyage;
import com.chris64233.commodityoperations.laytime.exception.EventConflictException;
import com.chris64233.commodityoperations.laytime.exception.LaytimeValidationException;
import com.chris64233.commodityoperations.laytime.exception.NotFoundException;
import com.chris64233.commodityoperations.laytime.exception.SettlementConflictException;
import com.chris64233.commodityoperations.laytime.repository.OperationEventRepository;
import com.chris64233.commodityoperations.laytime.repository.SettlementRepository;
import com.chris64233.commodityoperations.laytime.repository.VoyageRepository;
import com.chris64233.commodityoperations.laytime.service.SnapshotDtos.DifferenceEntry;
import com.chris64233.commodityoperations.laytime.service.SnapshotDtos.RawEventView;
import com.chris64233.commodityoperations.laytime.service.SnapshotDtos.SettlementView;
import com.chris64233.commodityoperations.laytime.service.SnapshotDtos.SuspensionView;
import com.chris64233.commodityoperations.laytime.service.SnapshotDtos.TimelinePoint;
import com.chris64233.commodityoperations.laytime.service.SnapshotDtos.VoyageDetailView;
import com.chris64233.commodityoperations.laytime.web.EventRequest;
import com.chris64233.commodityoperations.laytime.web.VoyageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class LaytimeService {

    private static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(3600);

    private final VoyageRepository voyageRepository;
    private final OperationEventRepository eventRepository;
    private final SettlementRepository settlementRepository;
    private final SnapshotMapper snapshotMapper;
    private final Clock clock;
    private final EntityManager entityManager;

    public LaytimeService(VoyageRepository voyageRepository,
                          OperationEventRepository eventRepository,
                          SettlementRepository settlementRepository,
                          SnapshotMapper snapshotMapper,
                          Clock clock,
                          EntityManager entityManager) {
        this.voyageRepository = voyageRepository;
        this.eventRepository = eventRepository;
        this.settlementRepository = settlementRepository;
        this.snapshotMapper = snapshotMapper;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    // ---- 1. 航次与事件登记 ----

    @Transactional
    public Voyage registerVoyage(VoyageRequest request) {
        if (voyageRepository.existsByVoyageNo(request.voyageNo())) {
            throw new LaytimeValidationException("航次号已存在：" + request.voyageNo());
        }
        StopRule rule = StopRule.valueOf(request.resolvedStopRule());
        long allowedSeconds = Duration.parse(request.allowedLaytime()).getSeconds();
        if (allowedSeconds <= 0) {
            throw new LaytimeValidationException("允许装卸时间必须大于 0");
        }
        return voyageRepository.save(new Voyage(
                request.voyageNo(),
                allowedSeconds,
                request.demurrageRatePerHour(),
                request.currency(),
                rule));
    }

    /**
     * 登记事件。同号同内容重放幂等；同号内容变化抛冲突；新事件使航次事件版本 +1。
     *
     * @return true 表示接受了新事件，false 表示幂等重放
     */
    @Transactional
    public boolean ingestEvent(String voyageNo, EventRequest request) {
        Voyage voyage = lockVoyage(voyageNo);
        EventType type = parseEventType(request.eventType());

        OperationEvent existing = eventRepository
                .findByVoyageIdAndExternalEventNo(voyage.getId(), request.externalEventNo())
                .orElse(null);
        if (existing != null) {
            if (existing.getEventType() == type && existing.getOccurredAt().equals(request.occurredAt())) {
                return false;
            }
            throw new EventConflictException("外部事件号 " + request.externalEventNo()
                    + " 已登记为 " + existing.getEventType().name() + " @ " + existing.getOccurredAt()
                    + "，本次内容 " + type.name() + " @ " + request.occurredAt() + " 与之冲突");
        }

        eventRepository.save(new OperationEvent(
                voyage, request.externalEventNo(), type, request.occurredAt(), Instant.now(clock)));
        voyage.incrementEventVersion();
        return true;
    }

    // ---- 2/3. 结算生成与重算 ----

    /**
     * 基于当前完整事件版本生成草稿结算：
     * 无历史版本 → 初版；当前为草稿 → 新版本草稿替换其当前地位；
     * 当前为已确认版本且有迟到事件 → 必须给出调整原因，生成差额调整版本，原版本不变。
     */
    @Transactional
    public SettlementView generateSettlement(String voyageNo, String reason) {
        Voyage voyage = lockVoyage(voyageNo);
        Settlement current = settlementRepository
                .findCurrentByVoyageIdForUpdate(voyage.getId()).orElse(null);

        if (current != null
                && current.getPinnedEventVersion() == voyage.getEventVersion()
                && (current.getStatus() == SettlementStatus.CONFIRMED
                    || (reason == null || reason.isBlank()))) {
            // 事件版本未变化：已确认版本保持不变；草稿重复生成直接幂等返回
            return snapshotMapper.toView(current, voyage.getVoyageNo());
        }
        if (current != null && current.getStatus() == SettlementStatus.CONFIRMED
                && (reason == null || reason.isBlank())) {
            throw new SettlementConflictException(
                    "结算版本 V" + current.getVersionNo() + " 已确认；迟到事件只能发起重算，"
                            + "且必须提供调整原因");
        }

        List<OperationEvent> events =
                eventRepository.findByVoyage_IdOrderByOccurredAtAscIdAsc(voyage.getId());
        List<RawEvent> raw = events.stream()
                .map(e -> new RawEvent(e.getExternalEventNo(), e.getEventType(), e.getOccurredAt()))
                .toList();

        // 先完成全部校验与计算；此处抛出异常则事务回滚，不会产生半张结算单
        NormalizedTimeline timeline = LaytimeCalculator.normalize(raw, voyage.getStopRule());
        long excess = Math.max(0, timeline.usedSeconds() - voyage.getAllowedLaytimeSeconds());
        BigDecimal demurrage = demurrage(excess, voyage.getDemurrageRatePerHour());

        List<TimelinePoint> timelinePoints = snapshotMapper.timeline(timeline);
        List<SuspensionView> suspensions = snapshotMapper.suspensions(timeline.suspensions());
        List<String> refs = timeline.orderedEvents().stream().map(RawEvent::externalEventNo).toList();

        int nextVersion = settlementRepository.findByVoyage_IdOrderByVersionNoAsc(voyage.getId())
                .stream().mapToInt(Settlement::getVersionNo).max().orElse(0) + 1;

        Settlement draft = new Settlement(
                voyage,
                nextVersion,
                voyage.getEventVersion(),
                voyage.getAllowedLaytimeSeconds(),
                voyage.getDemurrageRatePerHour(),
                voyage.getCurrency(),
                voyage.getStopRule(),
                timeline.startAt(),
                timeline.completeAt(),
                timeline.usedSeconds(),
                excess,
                demurrage,
                snapshotMapper.write(refs),
                snapshotMapper.write(timelinePoints),
                snapshotMapper.write(suspensions),
                Instant.now(clock));

        if (current != null && current.getStatus() == SettlementStatus.CONFIRMED) {
            draft.markAdjustment(current.getId(), reason,
                    snapshotMapper.write(buildDifferences(current, draft, timeline)));
        }

        if (current != null) {
            current.markSuperseded();
        }
        settlementRepository.save(draft);
        return snapshotMapper.toView(draft, voyage.getVoyageNo());
    }

    /**
     * 确认结算。只有固定在最新完整事件版本上的当前草稿可以确认；
     * 重算与确认并发时，落后版本的确认被拒绝，已确认金额永不改变。
     */
    @Transactional
    public SettlementView confirmSettlement(String voyageNo, Integer expectedVersionNo) {
        Voyage voyage = lockVoyage(voyageNo);
        Settlement current = settlementRepository
                .findCurrentByVoyageIdForUpdate(voyage.getId())
                .orElseThrow(() -> new SettlementConflictException("当前没有可确认的结算版本"));
        if (expectedVersionNo != null && current.getVersionNo() != expectedVersionNo) {
            throw new SettlementConflictException(
                    "请求确认的版本 V" + expectedVersionNo + " 已不是当前版本（当前为 V"
                            + current.getVersionNo() + "），只有基于最新完整事件版本的结果"
                            + "才能成为当前版本");
        }
        if (current.getStatus() == SettlementStatus.CONFIRMED) {
            throw new SettlementConflictException(
                    "结算版本 V" + current.getVersionNo() + " 已确认，不能重复确认");
        }
        if (current.getPinnedEventVersion() != voyage.getEventVersion()) {
            throw new SettlementConflictException(
                    "结算版本 V" + current.getVersionNo() + " 基于事件版本 "
                            + current.getPinnedEventVersion()
                            + "，而最新完整事件版本为 " + voyage.getEventVersion()
                            + "；只有基于最新完整事件版本的结果才能确认成为当前版本");
        }
        current.confirm(Instant.now(clock));
        return snapshotMapper.toView(current, voyage.getVoyageNo());
    }

    // ---- 5. 查询 ----

    @Transactional(readOnly = true)
    public VoyageDetailView getVoyageDetail(String voyageNo) {
        Voyage voyage = voyageRepository.findByVoyageNo(voyageNo)
                .orElseThrow(() -> new NotFoundException("航次不存在：" + voyageNo));
        List<RawEventView> rawEvents = eventRepository
                .findByVoyage_IdOrderByOccurredAtAscIdAsc(voyage.getId())
                .stream()
                .map(e -> new RawEventView(e.getExternalEventNo(), e.getEventType().name(),
                        e.getOccurredAt(), e.getReceivedAt()))
                .toList();
        List<SettlementView> settlements = settlementRepository
                .findByVoyage_IdOrderByVersionNoAsc(voyage.getId())
                .stream()
                .map(s -> snapshotMapper.toView(s, voyage.getVoyageNo()))
                .toList();
        return new VoyageDetailView(
                voyage.getVoyageNo(),
                voyage.getAllowedLaytimeSeconds(),
                voyage.getDemurrageRatePerHour(),
                voyage.getCurrency(),
                voyage.getStopRule().name(),
                voyage.getEventVersion(),
                rawEvents,
                settlements);
    }

    // ---- 内部方法 ----

    private Voyage lockVoyage(String voyageNo) {
        Long voyageId = voyageRepository.findByVoyageNo(voyageNo)
                .orElseThrow(() -> new NotFoundException("航次不存在：" + voyageNo)).getId();
        Voyage managed = entityManager.find(Voyage.class, voyageId);
        // refresh 带 PESSIMISTIC_WRITE：先获得行锁（阻塞至对方事务提交），再以最新提交状态重载，
        // 保证并发确认/重算不会使用会话内过期的事件版本号
        entityManager.refresh(managed, LockModeType.PESSIMISTIC_WRITE,
                Map.of("jakarta.persistence.lock.timeout", 10000));
        return managed;
    }

    private EventType parseEventType(String value) {
        try {
            return EventType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new LaytimeValidationException(
                    "未知事件类型：" + value + "，允许值为 BERTH/START/PAUSE/RESUME/COMPLETE");
        }
    }

    private BigDecimal demurrage(long excessSeconds, BigDecimal ratePerHour) {
        if (excessSeconds <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(excessSeconds)
                .multiply(ratePerHour)
                .divide(SECONDS_PER_HOUR, 2, RoundingMode.HALF_UP);
    }

    private List<DifferenceEntry> buildDifferences(Settlement oldVersion, Settlement newVersion,
                                                   NormalizedTimeline newTimeline) {
        return List.of(
                new DifferenceEntry(
                        "PINNED_EVENT_VERSION",
                        BigDecimal.valueOf(oldVersion.getPinnedEventVersion()),
                        BigDecimal.valueOf(newVersion.getPinnedEventVersion()),
                        BigDecimal.valueOf(newVersion.getPinnedEventVersion()
                                - oldVersion.getPinnedEventVersion()),
                        "重算纳入的迟到/新增事件版本"),
                new DifferenceEntry(
                        "DEDUCTED_SECONDS",
                        seconds(oldVersion.getCompleteAt().getEpochSecond()
                                - oldVersion.getStartAt().getEpochSecond() - oldVersion.getUsedSeconds()),
                        seconds(newTimeline.deductedSeconds()),
                        seconds(newTimeline.deductedSeconds()
                                - (oldVersion.getCompleteAt().getEpochSecond()
                                - oldVersion.getStartAt().getEpochSecond() - oldVersion.getUsedSeconds())),
                        "停算区间合并去重后的扣除时长"),
                new DifferenceEntry(
                        "USED_SECONDS",
                        seconds(oldVersion.getUsedSeconds()),
                        seconds(newVersion.getUsedSeconds()),
                        seconds(newVersion.getUsedSeconds() - oldVersion.getUsedSeconds()),
                        "有效占用时间变化"),
                new DifferenceEntry(
                        "EXCESS_SECONDS",
                        seconds(oldVersion.getExcessSeconds()),
                        seconds(newVersion.getExcessSeconds()),
                        seconds(newVersion.getExcessSeconds() - oldVersion.getExcessSeconds()),
                        "超出允许装卸时间的时长变化"),
                new DifferenceEntry(
                        "DEMURRAGE_AMOUNT",
                        oldVersion.getDemurrageAmount(),
                        newVersion.getDemurrageAmount(),
                        newVersion.getDemurrageAmount().subtract(oldVersion.getDemurrageAmount()),
                        "滞期费差额（" + newVersion.getCurrency() + "），调整原因："
                                + Objects.toString(newVersion.getAdjustmentReason(), "")));
    }

    private static BigDecimal seconds(long value) {
        return BigDecimal.valueOf(value);
    }
}
