package com.chris64233.commodityoperations.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "vessel_nomination", indexes = {
        @Index(name = "idx_nomination_contract", columnList = "contract_id"),
        @Index(name = "idx_nomination_vessel", columnList = "vesselCode"),
        @Index(name = "idx_nomination_active_vessel", columnList = "vesselCode,activeFlag")
})
public class VesselNomination {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(nullable = false)
    private String vesselCode;

    private String vesselName;

    @Column(nullable = false)
    private LocalDateTime estimatedArrival;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal plannedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NominationStatus status = NominationStatus.PENDING;

    @Column(nullable = false)
    private boolean activeFlag = true;

    @Column(unique = true)
    private String requestId;

    protected VesselNomination() {
    }

    public VesselNomination(Long contractId, String vesselCode, String vesselName,
                            LocalDateTime estimatedArrival, BigDecimal plannedQuantity,
                            String requestId) {
        this.contractId = contractId;
        this.vesselCode = vesselCode;
        this.vesselName = vesselName;
        this.estimatedArrival = estimatedArrival;
        this.plannedQuantity = plannedQuantity;
        this.requestId = requestId;
    }

    public void markConfirmed() {
        this.status = NominationStatus.CONFIRMED;
    }

    public void markWithdrawn() {
        this.status = NominationStatus.WITHDRAWN;
        this.activeFlag = false;
    }

    public void reschedule(LocalDateTime newEstimatedArrival) {
        this.estimatedArrival = newEstimatedArrival;
    }

    public Long getId() {
        return id;
    }

    public Long getContractId() {
        return contractId;
    }

    public String getVesselCode() {
        return vesselCode;
    }

    public String getVesselName() {
        return vesselName;
    }

    public LocalDateTime getEstimatedArrival() {
        return estimatedArrival;
    }

    public BigDecimal getPlannedQuantity() {
        return plannedQuantity;
    }

    public NominationStatus getStatus() {
        return status;
    }

    public boolean isActiveFlag() {
        return activeFlag;
    }

    public String getRequestId() {
        return requestId;
    }
}
