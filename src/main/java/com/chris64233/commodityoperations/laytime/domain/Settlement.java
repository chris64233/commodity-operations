package com.chris64233.commodityoperations.laytime.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * 结算单（版本化）。
 *
 * <p>每次基于当时完整的事件版本生成一张结算单；确认后金额冻结。迟到事件触发重算时
 * 生成新的版本，原版本的金额与调整原因永久保留，新版本通过 {@code previousVersionId}
 * 与 {@code adjustmentReason} 记录差额来源。</p>
 */
@Entity
@Table(name = "settlement")
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    @Column(name = "voyage_id", nullable = false, length = 36)
    private String voyageId;

    /** 同一航次内递增的结算版本号，从 1 开始。 */
    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SettlementStatus status = SettlementStatus.DRAFT;

    /** 生成时固定的合同规则快照（允许装卸时间、费率、币种、起算口径），JSON。 */
    @Lob
    @Column(name = "contract_snapshot", nullable = false)
    private String contractSnapshot;

    /** 生成时固定的事件版本：所有事件（外部号、类型、发生时间、接收时间）的哈希。 */
    @Column(name = "event_version_hash", nullable = false, length = 64)
    private String eventVersionHash;

    /** 参与本版本计算的事件数量。 */
    @Column(name = "event_count", nullable = false)
    private int eventCount;

    /** 起算时刻（靠泊或开工，取决于合同口径）。 */
    @Column(name = "counting_started_at", nullable = false)
    private Instant countingStartedAt;

    /** 完工时刻。 */
    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    /** 已用装卸时间（有效占用时间，秒）。 */
    @Column(name = "used_laytime_seconds", nullable = false)
    private long usedLaytimeSeconds;

    /** 暂停合计（秒），用于查询展示。 */
    @Column(name = "suspended_seconds", nullable = false)
    private long suspendedSeconds;

    /** 允许装卸时间（秒，快照自合同）。 */
    @Column(name = "allowed_laytime_seconds", nullable = false)
    private long allowedLaytimeSeconds;

    /** 超出允许时间的滞期时间（秒），未超则为 0。 */
    @Column(name = "excess_seconds", nullable = false)
    private long excessSeconds;

    /** 滞期费金额（本版本全额，非差额）。 */
    @Column(name = "demurrage_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal demurrageAmount;

    /** 相对上一版本的差额（本版本金额 - 上一版本金额），首版为 0。 */
    @Column(name = "adjustment_delta", nullable = false, precision = 18, scale = 2)
    private BigDecimal adjustmentDelta = BigDecimal.ZERO;

    /** 调整原因（首版为 INITIAL；重算时记录晚到事件号等原因）。 */
    @Column(name = "adjustment_reason", nullable = false, length = 512)
    private String adjustmentReason;

    /** 上一版本 id（首版为 null）。 */
    @Column(name = "previous_version_id", length = 36)
    private String previousVersionId;

    /** 是否当前版本（同一航次至多一行为 true）。 */
    @Column(name = "is_current", nullable = false)
    private boolean current;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Version
    @Column(name = "opt_lock")
    private Long optLock;

    protected Settlement() {
    }

    public Settlement(String voyageId, int versionNo, String contractSnapshot, String eventVersionHash,
                      int eventCount, Instant countingStartedAt, Instant completedAt,
                      Duration usedLaytime, Duration suspended, Duration allowedLaytime, Duration excess,
                      BigDecimal demurrageAmount, BigDecimal adjustmentDelta, String adjustmentReason,
                      String previousVersionId, boolean current) {
        this.voyageId = voyageId;
        this.versionNo = versionNo;
        this.contractSnapshot = contractSnapshot;
        this.eventVersionHash = eventVersionHash;
        this.eventCount = eventCount;
        this.countingStartedAt = countingStartedAt;
        this.completedAt = completedAt;
        this.usedLaytimeSeconds = usedLaytime.toSeconds();
        this.suspendedSeconds = suspended.toSeconds();
        this.allowedLaytimeSeconds = allowedLaytime.toSeconds();
        this.excessSeconds = excess.toSeconds();
        this.demurrageAmount = demurrageAmount;
        this.adjustmentDelta = adjustmentDelta;
        this.adjustmentReason = adjustmentReason;
        this.previousVersionId = previousVersionId;
        this.current = current;
    }

    /**
     * 卸下当前版本标记。已确认版本的状态与金额必须永久保留，只有草稿才转为 SUPERSEDED。
     */
    public void markSuperseded() {
        if (this.status != SettlementStatus.CONFIRMED) {
            this.status = SettlementStatus.SUPERSEDED;
        }
        this.current = false;
    }

    public void confirm() {
        this.status = SettlementStatus.CONFIRMED;
        this.confirmedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getVoyageId() { return voyageId; }
    public int getVersionNo() { return versionNo; }
    public SettlementStatus getStatus() { return status; }
    public String getContractSnapshot() { return contractSnapshot; }
    public String getEventVersionHash() { return eventVersionHash; }
    public int getEventCount() { return eventCount; }
    public Instant getCountingStartedAt() { return countingStartedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Duration getUsedLaytime() { return Duration.ofSeconds(usedLaytimeSeconds); }
    public long getUsedLaytimeSeconds() { return usedLaytimeSeconds; }
    public Duration getSuspended() { return Duration.ofSeconds(suspendedSeconds); }
    public long getSuspendedSeconds() { return suspendedSeconds; }
    public long getAllowedLaytimeSeconds() { return allowedLaytimeSeconds; }
    public Duration getExcess() { return Duration.ofSeconds(excessSeconds); }
    public long getExcessSeconds() { return excessSeconds; }
    public BigDecimal getDemurrageAmount() { return demurrageAmount; }
    public BigDecimal getAdjustmentDelta() { return adjustmentDelta; }
    public String getAdjustmentReason() { return adjustmentReason; }
    public String getPreviousVersionId() { return previousVersionId; }
    public boolean isCurrent() { return current; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
}
