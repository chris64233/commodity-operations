package com.chris64233.commodityoperations.laytime.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * 航次合同条款：允许装卸时间、滞期费率、计价币种与停算规则。
 */
@Entity
@Table(name = "voyage", uniqueConstraints = @UniqueConstraint(columnNames = "voyage_code"))
public class Voyage {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    /** 航次号（业务唯一键）。 */
    @Column(name = "voyage_code", nullable = false, length = 64)
    private String voyageCode;

    /** 船名（可选的描述信息）。 */
    @Column(length = 128)
    private String vesselName;

    /** 允许装卸时间（laytime allowance）。 */
    @Column(name = "allowed_laytime_seconds", nullable = false)
    private long allowedLaytimeSeconds;

    /** 滞期费率：每天（连续 24 小时）的金额。 */
    @Column(name = "demurrage_rate_per_day", nullable = false, precision = 18, scale = 4)
    private BigDecimal demurrageRatePerDay;

    /** 计价币种（ISO 4217，如 USD、CNY）。 */
    @Column(nullable = false, length = 3)
    private String currency;

    /** 装卸时间起算口径。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "counting_basis", nullable = false, length = 16)
    private CountingBasis countingBasis;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Voyage() {
    }

    public Voyage(String voyageCode, String vesselName, Duration allowedLaytime,
                  BigDecimal demurrageRatePerDay, String currency, CountingBasis countingBasis) {
        this.voyageCode = voyageCode;
        this.vesselName = vesselName;
        this.allowedLaytimeSeconds = allowedLaytime.toSeconds();
        this.demurrageRatePerDay = demurrageRatePerDay;
        this.currency = currency;
        this.countingBasis = countingBasis;
    }

    public String getId() {
        return id;
    }

    public String getVoyageCode() {
        return voyageCode;
    }

    public String getVesselName() {
        return vesselName;
    }

    public Duration getAllowedLaytime() {
        return Duration.ofSeconds(allowedLaytimeSeconds);
    }

    public long getAllowedLaytimeSeconds() {
        return allowedLaytimeSeconds;
    }

    public BigDecimal getDemurrageRatePerDay() {
        return demurrageRatePerDay;
    }

    public String getCurrency() {
        return currency;
    }

    public CountingBasis getCountingBasis() {
        return countingBasis;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
