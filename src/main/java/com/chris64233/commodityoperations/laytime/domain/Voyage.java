package com.chris64233.commodityoperations.laytime.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;

@Entity
@Table(name = "voyage")
public class Voyage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String voyageNo;

    /** 允许装卸时间（秒）。 */
    @Column(nullable = false)
    private long allowedLaytimeSeconds;

    /** 滞期费率，按小时计价。 */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal demurrageRatePerHour;

    @Column(nullable = false, length = 8)
    private String currency;

    @Column(nullable = false, length = 32)
    private String stopRule;

    /** 已接收事件版本号，每接受一个新事件递增 1。 */
    @Column(nullable = false)
    private long eventVersion;

    @Version
    private Long lockVersion;

    protected Voyage() {
    }

    public Voyage(String voyageNo, long allowedLaytimeSeconds, BigDecimal demurrageRatePerHour,
                  String currency, StopRule stopRule) {
        this.voyageNo = voyageNo;
        this.allowedLaytimeSeconds = allowedLaytimeSeconds;
        this.demurrageRatePerHour = demurrageRatePerHour;
        this.currency = currency;
        this.stopRule = stopRule.name();
        this.eventVersion = 0;
    }

    public Long getId() {
        return id;
    }

    public String getVoyageNo() {
        return voyageNo;
    }

    public long getAllowedLaytimeSeconds() {
        return allowedLaytimeSeconds;
    }

    public BigDecimal getDemurrageRatePerHour() {
        return demurrageRatePerHour;
    }

    public String getCurrency() {
        return currency;
    }

    public StopRule getStopRule() {
        return StopRule.valueOf(stopRule);
    }

    public long getEventVersion() {
        return eventVersion;
    }

    public void incrementEventVersion() {
        this.eventVersion++;
    }
}
