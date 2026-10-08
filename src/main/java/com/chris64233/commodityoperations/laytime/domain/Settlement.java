package com.chris64233.commodityoperations.laytime.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "settlement")
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voyage_id", nullable = false)
    private Voyage voyage;

    @Column(nullable = false)
    private int versionNo;

    @Column(nullable = false, length = 16)
    private String status;

    /** 是否为当前版本（草稿或最新确认版本）。历史版本固定为 false。 */
    @Column(nullable = false)
    private boolean current;

    /** 生成时固定的事件版本。 */
    @Column(nullable = false)
    private long pinnedEventVersion;

    // ---- 生成时固定的合同规则快照 ----
    @Column(nullable = false)
    private long allowedLaytimeSeconds;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal demurrageRatePerHour;

    @Column(nullable = false, length = 8)
    private String currency;

    @Column(nullable = false, length = 32)
    private String stopRule;

    // ---- 计算结果 ----
    @Column(nullable = false)
    private Instant startAt;

    @Column(nullable = false)
    private Instant completeAt;

    @Column(nullable = false)
    private long usedSeconds;

    @Column(nullable = false)
    private long excessSeconds;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal demurrageAmount;

    /** 纳入本版本的外部事件号 JSON 数组。 */
    @Lob
    @Column(nullable = false)
    private String eventRefsJson;

    /** 规范化时间线快照 JSON。 */
    @Lob
    @Column(nullable = false)
    private String timelineJson;

    /** 停算区间快照 JSON。 */
    @Lob
    @Column(nullable = false)
    private String suspensionsJson;

    // ---- 差额调整版本信息（初版为空） ----
    private Long basedOnSettlementId;

    private String adjustmentReason;

    /** 差额来源明细 JSON 数组。 */
    @Lob
    @Column
    private String differencesJson;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant confirmedAt;

    protected Settlement() {
    }

    public Settlement(Voyage voyage, int versionNo, long pinnedEventVersion,
                      long allowedLaytimeSeconds, BigDecimal demurrageRatePerHour,
                      String currency, StopRule stopRule,
                      Instant startAt, Instant completeAt, long usedSeconds, long excessSeconds,
                      BigDecimal demurrageAmount, String eventRefsJson, String timelineJson,
                      String suspensionsJson, Instant createdAt) {
        this.voyage = voyage;
        this.versionNo = versionNo;
        this.status = SettlementStatus.DRAFT.name();
        this.current = true;
        this.pinnedEventVersion = pinnedEventVersion;
        this.allowedLaytimeSeconds = allowedLaytimeSeconds;
        this.demurrageRatePerHour = demurrageRatePerHour;
        this.currency = currency;
        this.stopRule = stopRule.name();
        this.startAt = startAt;
        this.completeAt = completeAt;
        this.usedSeconds = usedSeconds;
        this.excessSeconds = excessSeconds;
        this.demurrageAmount = demurrageAmount;
        this.eventRefsJson = eventRefsJson;
        this.timelineJson = timelineJson;
        this.suspensionsJson = suspensionsJson;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Voyage getVoyage() {
        return voyage;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public SettlementStatus getStatus() {
        return SettlementStatus.valueOf(status);
    }

    public boolean isCurrent() {
        return current;
    }

    public long getPinnedEventVersion() {
        return pinnedEventVersion;
    }

    public long getAllowedLaytimeSeconds() {
        return allowedLaytimeSeconds;
    }

    public BigDecimal getDemurrageRatePerHour() {
        return demurrageRatePerHour;
    }

    public String getCurrency() {
        return currency;
    }

    public StopRule getStopRule() {
        return StopRule.valueOf(stopRule);
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getCompleteAt() {
        return completeAt;
    }

    public long getUsedSeconds() {
        return usedSeconds;
    }

    public long getExcessSeconds() {
        return excessSeconds;
    }

    public BigDecimal getDemurrageAmount() {
        return demurrageAmount;
    }

    public String getEventRefsJson() {
        return eventRefsJson;
    }

    public String getTimelineJson() {
        return timelineJson;
    }

    public String getSuspensionsJson() {
        return suspensionsJson;
    }

    public Long getBasedOnSettlementId() {
        return basedOnSettlementId;
    }

    public String getAdjustmentReason() {
        return adjustmentReason;
    }

    public String getDifferencesJson() {
        return differencesJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void markSuperseded() {
        this.current = false;
    }

    public void markAdjustment(Long basedOnSettlementId, String reason, String differencesJson) {
        this.basedOnSettlementId = basedOnSettlementId;
        this.adjustmentReason = reason;
        this.differencesJson = differencesJson;
    }

    public void confirm(Instant confirmedAt) {
        this.status = SettlementStatus.CONFIRMED.name();
        this.confirmedAt = confirmedAt;
        this.current = true;
    }
}
