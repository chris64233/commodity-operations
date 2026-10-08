package com.chris64233.commodityoperations.pricing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "market_prices", uniqueConstraints = @UniqueConstraint(
        name = "uk_market_price_version", columnNames = {"source", "commodity", "currency", "priceVersion"}))
public class MarketPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private String commodity;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 38, scale = 6)
    private BigDecimal price;

    @Column(nullable = false)
    private long priceVersion;

    @Column(nullable = false)
    private Instant validFrom;

    @Column(nullable = false)
    private Instant validTo;

    protected MarketPrice() {
    }

    public MarketPrice(String source, String commodity, String currency, BigDecimal price,
                       long priceVersion, Instant validFrom, Instant validTo) {
        this.source = source;
        this.commodity = commodity;
        this.currency = currency;
        this.price = price;
        this.priceVersion = priceVersion;
        this.validFrom = validFrom;
        this.validTo = validTo;
    }

    public boolean isValidAt(Instant instant) {
        return !instant.isBefore(validFrom) && !instant.isAfter(validTo);
    }

    public Long getId() {
        return id;
    }

    public String getSource() {
        return source;
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

    public long getPriceVersion() {
        return priceVersion;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidTo() {
        return validTo;
    }
}
