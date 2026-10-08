package com.chris64233.commodityoperations.contract;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Entity
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String commodity;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal baseQuantity;

    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal shortTolerancePercent;

    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal overTolerancePercent;

    @Column(nullable = false)
    private LocalDate deliveryDeadline;

    @Column(nullable = false)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractStatus status = ContractStatus.OPEN;

    @Version
    private long version;

    protected Contract() {
    }

    public Contract(String commodity, BigDecimal baseQuantity, BigDecimal shortTolerancePercent,
                    BigDecimal overTolerancePercent, LocalDate deliveryDeadline, String unit) {
        this.commodity = commodity;
        this.baseQuantity = baseQuantity;
        this.shortTolerancePercent = shortTolerancePercent;
        this.overTolerancePercent = overTolerancePercent;
        this.deliveryDeadline = deliveryDeadline;
        this.unit = unit;
    }

    public BigDecimal minAcceptableQuantity() {
        return baseQuantity.multiply(
                BigDecimal.ONE.subtract(shortTolerancePercent.movePointLeft(2)))
                .setScale(4, RoundingMode.HALF_UP);
    }

    public BigDecimal maxAcceptableQuantity() {
        return baseQuantity.multiply(
                BigDecimal.ONE.add(overTolerancePercent.movePointLeft(2)))
                .setScale(4, RoundingMode.HALF_UP);
    }

    public Long getId() {
        return id;
    }

    public String getCommodity() {
        return commodity;
    }

    public BigDecimal getBaseQuantity() {
        return baseQuantity;
    }

    public BigDecimal getShortTolerancePercent() {
        return shortTolerancePercent;
    }

    public BigDecimal getOverTolerancePercent() {
        return overTolerancePercent;
    }

    public LocalDate getDeliveryDeadline() {
        return deliveryDeadline;
    }

    public String getUnit() {
        return unit;
    }

    public ContractStatus getStatus() {
        return status;
    }

    public void setStatus(ContractStatus status) {
        this.status = status;
    }
}
