package com.chris64233.commodityoperations.vesselnomination;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "vessel_nomination")
public class VesselNomination {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private LoadingContract contract;

    @Column(nullable = false)
    private String vesselName;

    @Column(nullable = false)
    private LocalDate eta;

    @Column(nullable = false)
    private long plannedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NominationStatus status;

    private LocalDate reservedDate;

    protected VesselNomination() {
    }

    public VesselNomination(LoadingContract contract, String vesselName, LocalDate eta, long plannedQuantity) {
        this.contract = contract;
        this.vesselName = vesselName;
        this.eta = eta;
        this.plannedQuantity = plannedQuantity;
        this.status = NominationStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public LoadingContract getContract() {
        return contract;
    }

    public String getVesselName() {
        return vesselName;
    }

    public LocalDate getEta() {
        return eta;
    }

    public long getPlannedQuantity() {
        return plannedQuantity;
    }

    public NominationStatus getStatus() {
        return status;
    }

    public LocalDate getReservedDate() {
        return reservedDate;
    }

    void confirm(LocalDate date) {
        this.status = NominationStatus.CONFIRMED;
        this.reservedDate = date;
    }

    void withdraw() {
        this.status = NominationStatus.WITHDRAWN;
    }

    void moveTo(LocalDate newDate) {
        this.reservedDate = newDate;
    }
}
