package com.chris64233.commodityoperations.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(name = "daily_capacity", uniqueConstraints =
        @UniqueConstraint(name = "uk_daily_capacity_date", columnNames = "cap_date"))
public class DailyCapacity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cap_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal availableCapacity;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal reservedCapacity = BigDecimal.ZERO;

    @Version
    @Column(nullable = false)
    private long version;

    protected DailyCapacity() {
    }

    public DailyCapacity(LocalDate date, BigDecimal availableCapacity) {
        this.date = date;
        this.availableCapacity = availableCapacity;
        this.reservedCapacity = BigDecimal.ZERO;
    }

    public boolean canReserve(BigDecimal quantity) {
        return reservedCapacity.add(quantity).compareTo(availableCapacity) <= 0;
    }

    public void reserve(BigDecimal quantity) {
        this.reservedCapacity = reservedCapacity.add(quantity);
    }

    public void release(BigDecimal quantity) {
        this.reservedCapacity = reservedCapacity.subtract(quantity);
    }

    public Long getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public BigDecimal getAvailableCapacity() {
        return availableCapacity;
    }

    public BigDecimal getReservedCapacity() {
        return reservedCapacity;
    }

    public long getVersion() {
        return version;
    }
}
