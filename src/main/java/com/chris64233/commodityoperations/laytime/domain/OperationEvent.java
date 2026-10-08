package com.chris64233.commodityoperations.laytime.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "operation_event", uniqueConstraints =
        @UniqueConstraint(name = "uk_event_voyage_external_no",
                columnNames = {"voyage_id", "external_event_no"}))
public class OperationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voyage_id", nullable = false)
    private Voyage voyage;

    /** 外部事件号，幂等键。 */
    @Column(name = "external_event_no", nullable = false)
    private String externalEventNo;

    @Column(nullable = false, length = 16)
    private String eventType;

    /** 事件发生时间（业务时间，可能晚到、乱序到达）。 */
    @Column(nullable = false)
    private Instant occurredAt;

    /** 系统接收时间，以首次接收为准，重放不变。 */
    @Column(nullable = false)
    private Instant receivedAt;

    protected OperationEvent() {
    }

    public OperationEvent(Voyage voyage, String externalEventNo, EventType eventType,
                          Instant occurredAt, Instant receivedAt) {
        this.voyage = voyage;
        this.externalEventNo = externalEventNo;
        this.eventType = eventType.name();
        this.occurredAt = occurredAt;
        this.receivedAt = receivedAt;
    }

    public Long getId() {
        return id;
    }

    public Voyage getVoyage() {
        return voyage;
    }

    public String getExternalEventNo() {
        return externalEventNo;
    }

    public EventType getEventType() {
        return EventType.valueOf(eventType);
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
