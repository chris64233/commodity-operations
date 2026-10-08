package com.chris64233.commodityoperations.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "vessel_guard")
public class VesselGuard {

    @Id
    @Column(length = 64)
    private String vesselCode;

    protected VesselGuard() {
    }

    public VesselGuard(String vesselCode) {
        this.vesselCode = vesselCode;
    }

    public String getVesselCode() {
        return vesselCode;
    }
}
