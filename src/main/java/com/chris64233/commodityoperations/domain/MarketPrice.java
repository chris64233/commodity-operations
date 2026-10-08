package com.chris64233.commodityoperations.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 市场价格的一个不可变版本（快照）。
 * 同一来源 + 价格版本号唯一；validFrom/validTo 决定该版本的有效引用窗口。
 */
@Entity
@Table(name = "market_prices",
        uniqueConstraints = @UniqueConstraint(columnNames = {"source", "price_version"}))
public class MarketPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String source;

    @Column(name = "price_version", nullable = false, length = 64)
    private String priceVersion;

    @Column(nullable = false, length = 64)
    private String commodity;

    @Column(nullable = false, length = 8)
    private String currency;

    @Column(nullable = false, precision = 30, scale = 8)
    private BigDecimal price;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to", nullable = false)
    private Instant validTo;

    protected MarketPrice() {
    }

    public MarketPrice(String source, String priceVersion, String commodity, String currency,
                       BigDecimal price, Instant validFrom, Instant validTo) {
        this.source = source;
        this.priceVersion = priceVersion;
        this.commodity = commodity;
        this.currency = currency;
        this.price = price;
        this.validFrom = validFrom;
        this.validTo = validTo;
    }

    public boolean isValidAt(Instant time) {
        return !time.isBefore(validFrom) && !time.isAfter(validTo);
    }

    public Long getId() {
        return id;
    }

    public String getSource() {
        return source;
    }

    public String getPriceVersion() {
        return priceVersion;
    }

    public String getCommodity() {
        return commodity;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidTo() {
        return validTo;
    }
}
