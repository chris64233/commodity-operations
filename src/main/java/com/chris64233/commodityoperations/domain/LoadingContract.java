package com.chris64233.commodityoperations.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "loading_contract")
public class LoadingContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String contractCode;

    private String counterparty;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal allowedQuantity;

    @Column(nullable = false)
    private LocalDate windowStart;

    @Column(nullable = false)
    private LocalDate windowEnd;

    protected LoadingContract() {
    }

    public LoadingContract(String contractCode, String counterparty, BigDecimal allowedQuantity,
                           LocalDate windowStart, LocalDate windowEnd) {
        this.contractCode = contractCode;
        this.counterparty = counterparty;
        this.allowedQuantity = allowedQuantity;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
    }

    public Long getId() {
        return id;
    }

    public String getContractCode() {
        return contractCode;
    }

    public String getCounterparty() {
        return counterparty;
    }

    public BigDecimal getAllowedQuantity() {
        return allowedQuantity;
    }

    public LocalDate getWindowStart() {
        return windowStart;
    }

    public LocalDate getWindowEnd() {
        return windowEnd;
    }
}
