package com.chris64233.commodityoperations.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "contracts", uniqueConstraints = @UniqueConstraint(columnNames = "contract_no"))
public class Contract {

    @Version
    private long version;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_no", nullable = false, length = 64)
    private String contractNo;

    @Column(nullable = false, length = 64)
    private String commodity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ContractDirection direction;

    /** 合同数量，正数，按商品自身计量精度。 */
    @Column(name = "contract_qty", nullable = false, precision = 30, scale = 8)
    private BigDecimal contractQty;

    /** 已点价累计数量。 */
    @Column(name = "priced_qty", nullable = false, precision = 30, scale = 8)
    private BigDecimal pricedQty = BigDecimal.ZERO;

    @Column(nullable = false, length = 8)
    private String currency;

    /** 暂定单价。 */
    @Column(name = "provisional_price", nullable = false, precision = 30, scale = 8)
    private BigDecimal provisionalPrice;

    /** 暂定金额 = 暂定单价 × 合同数量，登记时固定。 */
    @Column(name = "provisional_amount", nullable = false, precision = 30, scale = 6)
    private BigDecimal provisionalAmount;

    @Column(name = "pricing_window_start", nullable = false)
    private Instant pricingWindowStart;

    @Column(name = "pricing_window_end", nullable = false)
    private Instant pricingWindowEnd;

    /** 允许引用的市场价格来源，逗号分隔。 */
    @Column(name = "allowed_sources", nullable = false, length = 1024)
    private String allowedSources;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ContractStatus status = ContractStatus.OPEN;

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PricingOrder> pricingOrders = new LinkedHashSet<>();

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("versionNo ASC")
    private Set<SettlementVersion> settlementVersions = new LinkedHashSet<>();

    protected Contract() {
    }

    public Contract(String contractNo, String commodity, ContractDirection direction,
                    BigDecimal contractQty, String currency, BigDecimal provisionalPrice,
                    Instant pricingWindowStart, Instant pricingWindowEnd, Set<String> allowedSources) {
        this.contractNo = contractNo;
        this.commodity = commodity;
        this.direction = direction;
        this.contractQty = contractQty;
        this.currency = currency;
        this.provisionalPrice = provisionalPrice;
        this.provisionalAmount = MoneyCalculations.amount(this.provisionalPrice, this.contractQty);
        this.allowedSources = String.join(",", allowedSources);
        this.pricingWindowStart = pricingWindowStart;
        this.pricingWindowEnd = pricingWindowEnd;
    }

    public BigDecimal unpricedQty() {
        return this.contractQty.subtract(this.pricedQty);
    }

    public void addPricedQty(BigDecimal qty) {
        this.pricedQty = this.pricedQty.add(qty);
        if (this.pricedQty.compareTo(this.contractQty) >= 0) {
            this.status = ContractStatus.FULLY_PRICED;
        }
    }

    public void markFinallySettled() {
        this.status = ContractStatus.FINALLY_SETTLED;
    }

    public boolean allowsSource(String source) {
        for (String allowed : this.allowedSources.split(",")) {
            if (allowed.equals(source)) {
                return true;
            }
        }
        return false;
    }

    public long getVersion() {
        return version;
    }

    public Long getId() {
        return id;
    }

    public String getContractNo() {
        return contractNo;
    }

    public String getCommodity() {
        return commodity;
    }

    public ContractDirection getDirection() {
        return direction;
    }

    public BigDecimal getContractQty() {
        return contractQty;
    }

    public BigDecimal getPricedQty() {
        return pricedQty;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getProvisionalPrice() {
        return provisionalPrice;
    }

    public BigDecimal getProvisionalAmount() {
        return provisionalAmount;
    }

    public Instant getPricingWindowStart() {
        return pricingWindowStart;
    }

    public Instant getPricingWindowEnd() {
        return pricingWindowEnd;
    }

    public Set<String> getAllowedSources() {
        Set<String> sources = new LinkedHashSet<>();
        for (String source : allowedSources.split(",")) {
            if (!source.isBlank()) {
                sources.add(source);
            }
        }
        return sources;
    }

    public ContractStatus getStatus() {
        return status;
    }

    public Set<PricingOrder> getPricingOrders() {
        return pricingOrders;
    }

    public Set<SettlementVersion> getSettlementVersions() {
        return settlementVersions;
    }
}
