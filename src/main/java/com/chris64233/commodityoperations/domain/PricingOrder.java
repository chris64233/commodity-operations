package com.chris64233.commodityoperations.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 一次点价：覆盖合同部分未点价数量，固定市场价格版本、升贴水与点价时间。
 * 外部点价号唯一，用于幂等与冲突检测。记录一旦写入不可修改。
 */
@Entity
@Table(name = "pricing_orders",
        uniqueConstraints = @UniqueConstraint(name = "uk_pricing_external_no", columnNames = "external_pricing_no"))
public class PricingOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Column(name = "external_pricing_no", nullable = false, length = 64)
    private String externalPricingNo;

    @Column(nullable = false, precision = 30, scale = 8)
    private BigDecimal quantity;

    @Column(name = "price_source", nullable = false, length = 64)
    private String priceSource;

    @Column(name = "price_version", nullable = false, length = 64)
    private String priceVersion;

    @Column(name = "market_price", nullable = false, precision = 30, scale = 8)
    private BigDecimal marketPrice;

    /** 升贴水（premium 为正，discount 为负）。 */
    @Column(name = "premium_discount", nullable = false, precision = 30, scale = 8)
    private BigDecimal premiumDiscount;

    /** 固定结算单价 = 市场价 + 升贴水。 */
    @Column(name = "fixed_price", nullable = false, precision = 30, scale = 8)
    private BigDecimal fixedPrice;

    /** 与暂定价相比，本次数量产生的价差调整金额。 */
    @Column(name = "adjustment_amount", nullable = false, precision = 30, scale = 6)
    private BigDecimal adjustmentAmount;

    @Column(name = "priced_at", nullable = false)
    private Instant pricedAt;

    protected PricingOrder() {
    }

    public PricingOrder(Contract contract, String externalPricingNo, BigDecimal quantity,
                        String priceSource, String priceVersion, BigDecimal marketPrice,
                        BigDecimal premiumDiscount, Instant pricedAt) {
        this.contract = contract;
        this.externalPricingNo = externalPricingNo;
        this.quantity = quantity;
        this.priceSource = priceSource;
        this.priceVersion = priceVersion;
        this.marketPrice = marketPrice;
        this.premiumDiscount = premiumDiscount;
        this.fixedPrice = marketPrice.add(premiumDiscount)
                .setScale(MoneyCalculations.PRICE_SCALE, MoneyCalculations.ROUNDING);
        BigDecimal unitDiff = this.fixedPrice.subtract(contract.getProvisionalPrice());
        this.adjustmentAmount = MoneyCalculations.amount(unitDiff, quantity);
        this.pricedAt = pricedAt;
    }

    public Long getId() {
        return id;
    }

    public Contract getContract() {
        return contract;
    }

    public String getExternalPricingNo() {
        return externalPricingNo;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public String getPriceSource() {
        return priceSource;
    }

    public String getPriceVersion() {
        return priceVersion;
    }

    public BigDecimal getMarketPrice() {
        return marketPrice;
    }

    public BigDecimal getPremiumDiscount() {
        return premiumDiscount;
    }

    public BigDecimal getFixedPrice() {
        return fixedPrice;
    }

    public BigDecimal getAdjustmentAmount() {
        return adjustmentAmount;
    }

    public Instant getPricedAt() {
        return pricedAt;
    }
}
