package com.chris64233.commodityoperations.pricing;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "contracts")
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String commodity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Direction direction;

    @Column(nullable = false, precision = 38, scale = 6)
    private BigDecimal quantity;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 38, scale = 6)
    private BigDecimal provisionalPrice;

    @Column(nullable = false)
    private Instant pricingWindowStart;

    @Column(nullable = false)
    private Instant pricingWindowEnd;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "contract_allowed_sources", joinColumns = @JoinColumn(name = "contract_id"))
    @Column(name = "source", nullable = false)
    private Set<String> allowedSources = new LinkedHashSet<>();

    @Column(nullable = false, precision = 38, scale = 6)
    private BigDecimal fixedQuantity = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractStatus status = ContractStatus.PROVISIONAL;

    @Version
    private long version;

    protected Contract() {
    }

    public Contract(String commodity, Direction direction, BigDecimal quantity, String currency,
                    BigDecimal provisionalPrice, Instant pricingWindowStart, Instant pricingWindowEnd,
                    Set<String> allowedSources) {
        this.commodity = commodity;
        this.direction = direction;
        this.quantity = quantity;
        this.currency = currency;
        this.provisionalPrice = provisionalPrice;
        this.pricingWindowStart = pricingWindowStart;
        this.pricingWindowEnd = pricingWindowEnd;
        this.allowedSources = new LinkedHashSet<>(allowedSources);
    }

    public BigDecimal remainingQuantity() {
        return quantity.subtract(fixedQuantity);
    }

    public void addFixedQuantity(BigDecimal amount) {
        this.fixedQuantity = this.fixedQuantity.add(amount);
        if (this.fixedQuantity.compareTo(this.quantity) > 0) {
            throw new IllegalStateException("累计点价数量超过合同数量");
        }
        if (this.fixedQuantity.compareTo(this.quantity) == 0) {
            this.status = ContractStatus.FULLY_FIXED;
        } else if (this.fixedQuantity.signum() > 0) {
            this.status = ContractStatus.PARTIALLY_FIXED;
        }
    }

    public void markFinalSettled() {
        this.status = ContractStatus.FINAL_SETTLED;
    }

    public Long getId() {
        return id;
    }

    public String getCommodity() {
        return commodity;
    }

    public Direction getDirection() {
        return direction;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getProvisionalPrice() {
        return provisionalPrice;
    }

    public Instant getPricingWindowStart() {
        return pricingWindowStart;
    }

    public Instant getPricingWindowEnd() {
        return pricingWindowEnd;
    }

    public Set<String> getAllowedSources() {
        return allowedSources;
    }

    public BigDecimal getFixedQuantity() {
        return fixedQuantity;
    }

    public ContractStatus getStatus() {
        return status;
    }
}
