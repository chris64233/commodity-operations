package com.chris64233.commodityoperations.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "delivery_record", uniqueConstraints =
        @UniqueConstraint(name = "uk_delivery_contract_external_ref", columnNames = {"contractId", "externalRef"}))
public class DeliveryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long contractId;

    @Column(nullable = false)
    private String externalRef;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(nullable = false)
    private LocalDate deliveryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryType type;

    private Long reversalOfId;

    protected DeliveryRecord() {
    }

    public DeliveryRecord(Long contractId, String externalRef, BigDecimal quantity,
                          LocalDate deliveryDate, DeliveryType type, Long reversalOfId) {
        this.contractId = contractId;
        this.externalRef = externalRef;
        this.quantity = quantity;
        this.deliveryDate = deliveryDate;
        this.type = type;
        this.reversalOfId = reversalOfId;
    }

    public Long getId() {
        return id;
    }

    public Long getContractId() {
        return contractId;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public LocalDate getDeliveryDate() {
        return deliveryDate;
    }

    public DeliveryType getType() {
        return type;
    }

    public Long getReversalOfId() {
        return reversalOfId;
    }
}
