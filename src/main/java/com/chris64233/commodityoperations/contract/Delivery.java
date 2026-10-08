package com.chris64233.commodityoperations.contract;

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
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"contract_id", "external_ref"}))
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "external_ref", nullable = false)
    private String externalRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryType type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(nullable = false)
    private LocalDateTime deliveredAt;

    private Long reversalOfId;

    @Column(nullable = false)
    private String payloadHash;

    @Column(nullable = false)
    private Instant recordedAt = Instant.now();

    protected Delivery() {
    }

    public Delivery(Long contractId, String externalRef, DeliveryType type, BigDecimal quantity,
                    LocalDateTime deliveredAt, Long reversalOfId, String payloadHash) {
        this.contractId = contractId;
        this.externalRef = externalRef;
        this.type = type;
        this.quantity = quantity;
        this.deliveredAt = deliveredAt;
        this.reversalOfId = reversalOfId;
        this.payloadHash = payloadHash;
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

    public DeliveryType getType() {
        return type;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public Long getReversalOfId() {
        return reversalOfId;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
