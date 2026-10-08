package com.chris64233.commodityoperations.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 结算流水中不可变的一版。历史版本永不覆盖，每次点价只追加新版本。
 */
@Entity
@Table(name = "settlement_versions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"contract_id", "version_no"}))
public class SettlementVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    /** 合同内从 1 开始递增的版本号。 */
    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private SettlementVersionType type;

    /** 产生该版本的点价单；暂定与最终版本为 null。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pricing_order_id")
    private PricingOrder pricingOrder;

    /** 本版本引入的金额变化（暂定版本为暂定金额，点价版本为价差，最终版本为 0）。 */
    @Column(name = "delta_amount", nullable = false, precision = 30, scale = 6)
    private BigDecimal deltaAmount;

    /** 截至本版本的累计调整金额（不含暂定金额）。 */
    @Column(name = "cumulative_adjustment", nullable = false, precision = 30, scale = 6)
    private BigDecimal cumulativeAdjustment;

    /** 截至本版本的应收/应付金额：暂定金额 + 累计调整。 */
    @Column(name = "total_amount", nullable = false, precision = 30, scale = 6)
    private BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SettlementVersion() {
    }

    public SettlementVersion(Contract contract, int versionNo, SettlementVersionType type,
                             PricingOrder pricingOrder, BigDecimal deltaAmount,
                             BigDecimal cumulativeAdjustment, Instant createdAt) {
        this.contract = contract;
        this.versionNo = versionNo;
        this.type = type;
        this.pricingOrder = pricingOrder;
        this.deltaAmount = deltaAmount;
        this.cumulativeAdjustment = cumulativeAdjustment;
        this.totalAmount = MoneyCalculations.normalizeAmount(
                contract.getProvisionalAmount().add(cumulativeAdjustment));
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Contract getContract() {
        return contract;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public SettlementVersionType getType() {
        return type;
    }

    public PricingOrder getPricingOrder() {
        return pricingOrder;
    }

    public BigDecimal getDeltaAmount() {
        return deltaAmount;
    }

    public BigDecimal getCumulativeAdjustment() {
        return cumulativeAdjustment;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
