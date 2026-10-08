package com.chris64233.commodityoperations.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "closure_record")
public class ClosureRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long contractId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal cumulativeQuantity;

    @Column(nullable = false)
    private Instant closedAt;

    @Column(nullable = false)
    private boolean superseded;

    protected ClosureRecord() {
    }

    public ClosureRecord(Long contractId, BigDecimal cumulativeQuantity, Instant closedAt) {
        this.contractId = contractId;
        this.cumulativeQuantity = cumulativeQuantity;
        this.closedAt = closedAt;
        this.superseded = false;
    }

    public Long getId() {
        return id;
    }

    public Long getContractId() {
        return contractId;
    }

    public BigDecimal getCumulativeQuantity() {
        return cumulativeQuantity;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public boolean isSuperseded() {
        return superseded;
    }

    public void markSuperseded() {
        this.superseded = true;
    }
}
