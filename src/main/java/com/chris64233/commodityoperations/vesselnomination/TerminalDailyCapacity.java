package com.chris64233.commodityoperations.vesselnomination;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;

@Entity
@Table(name = "terminal_daily_capacity",
        uniqueConstraints = @UniqueConstraint(columnNames = "capacityDate"))
public class TerminalDailyCapacity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate capacityDate;

    @Column(nullable = false)
    private long totalCapacity;

    @Column(nullable = false)
    private long reservedCapacity;

    protected TerminalDailyCapacity() {
    }

    public TerminalDailyCapacity(LocalDate capacityDate, long totalCapacity) {
        this.capacityDate = capacityDate;
        this.totalCapacity = totalCapacity;
        this.reservedCapacity = 0;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getCapacityDate() {
        return capacityDate;
    }

    public long getTotalCapacity() {
        return totalCapacity;
    }

    public long getReservedCapacity() {
        return reservedCapacity;
    }

    public long getAvailableCapacity() {
        return totalCapacity - reservedCapacity;
    }

    public void reserve(long quantity) {
        if (quantity <= 0 || getAvailableCapacity() < quantity) {
            throw new NominationException("码头 " + capacityDate + " 可用能力不足，剩余 " + getAvailableCapacity());
        }
        this.reservedCapacity += quantity;
    }

    public void release(long quantity) {
        if (quantity <= 0 || this.reservedCapacity < quantity) {
            throw new NominationException("码头 " + capacityDate + " 可释放能力不足");
        }
        this.reservedCapacity -= quantity;
    }
}
