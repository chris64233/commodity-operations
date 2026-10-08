package com.chris64233.commodityoperations.pricing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "price_fixings", uniqueConstraints = @UniqueConstraint(
        name = "uk_fixing_external_no", columnNames = {"contract_id", "externalFixingNo"}))
public class PriceFixing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Column(nullable = false)
    private String externalFixingNo;

    @Column(nullable = false, precision = 38, scale = 6)
    private BigDecimal quantity;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "market_price_id", nullable = false)
    private MarketPrice marketPrice;

    @Column(nullable = false, precision = 38, scale = 6)
    private BigDecimal basis;

    @Column(nullable = false, precision = 38, scale = 6)
    private BigDecimal fixedPrice;

    @Column(nullable = false)
    private Instant fixedAt;

    @Column(nullable = false, length = 64)
    private String requestHash;

    protected PriceFixing() {
    }

    public PriceFixing(Contract contract, String externalFixingNo, BigDecimal quantity,
                       MarketPrice marketPrice, BigDecimal basis, BigDecimal fixedPrice,
                       Instant fixedAt, String requestHash) {
        this.contract = contract;
        this.externalFixingNo = externalFixingNo;
        this.quantity = quantity;
        this.marketPrice = marketPrice;
        this.basis = basis;
        this.fixedPrice = fixedPrice;
        this.fixedAt = fixedAt;
        this.requestHash = requestHash;
    }

    public Long getId() {
        return id;
    }

    public Contract getContract() {
        return contract;
    }

    public String getExternalFixingNo() {
        return externalFixingNo;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public MarketPrice getMarketPrice() {
        return marketPrice;
    }

    public BigDecimal getBasis() {
        return basis;
    }

    public BigDecimal getFixedPrice() {
        return fixedPrice;
    }

    public Instant getFixedAt() {
        return fixedAt;
    }

    public String getRequestHash() {
        return requestHash;
    }
}
