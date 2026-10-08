package com.chris64233.commodityoperations.pricing;

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
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "settlement_entries", uniqueConstraints = @UniqueConstraint(
        name = "uk_settlement_contract_seq", columnNames = {"contract_id", "seq"}))
public class SettlementEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Column(nullable = false)
    private int seq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementEntryType type;

    @Column(nullable = false, precision = 38, scale = 6)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 38, scale = 6)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal amount;

    @Column
    private Long fixingId;

    @Column(nullable = false)
    private Instant createdAt;

    protected SettlementEntry() {
    }

    public SettlementEntry(Contract contract, int seq, SettlementEntryType type, BigDecimal quantity,
                           BigDecimal unitPrice, BigDecimal amount, Long fixingId, Instant createdAt) {
        this.contract = contract;
        this.seq = seq;
        this.type = type;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.amount = amount;
        this.fixingId = fixingId;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Contract getContract() {
        return contract;
    }

    public int getSeq() {
        return seq;
    }

    public SettlementEntryType getType() {
        return type;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Long getFixingId() {
        return fixingId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
