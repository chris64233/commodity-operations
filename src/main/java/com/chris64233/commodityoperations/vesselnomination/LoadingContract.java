package com.chris64233.commodityoperations.vesselnomination;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "loading_contract")
public class LoadingContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String contractNumber;

    @Column(nullable = false)
    private long allowedQuantity;

    @Column(nullable = false)
    private LocalDate windowStart;

    @Column(nullable = false)
    private LocalDate windowEnd;

    protected LoadingContract() {
    }

    public LoadingContract(String contractNumber, long allowedQuantity, LocalDate windowStart, LocalDate windowEnd) {
        this.contractNumber = contractNumber;
        this.allowedQuantity = allowedQuantity;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
    }

    public Long getId() {
        return id;
    }

    public String getContractNumber() {
        return contractNumber;
    }

    public long getAllowedQuantity() {
        return allowedQuantity;
    }

    public LocalDate getWindowStart() {
        return windowStart;
    }

    public LocalDate getWindowEnd() {
        return windowEnd;
    }

    public boolean covers(LocalDate date) {
        return !date.isBefore(windowStart) && !date.isAfter(windowEnd);
    }
}
