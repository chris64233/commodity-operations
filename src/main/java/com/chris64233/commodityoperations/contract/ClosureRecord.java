package com.chris64233.commodityoperations.contract;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
public class ClosureRecord {

    public enum State {
        ACTIVE,
        REOPENED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long contractId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal cumulativeQuantity;

    @Column(nullable = false)
    private Instant closedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private State state = State.ACTIVE;

    protected ClosureRecord() {
    }

    public ClosureRecord(Long contractId, BigDecimal cumulativeQuantity) {
        this.contractId = contractId;
        this.cumulativeQuantity = cumulativeQuantity;
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

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }
}
