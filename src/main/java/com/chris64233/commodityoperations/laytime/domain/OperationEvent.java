package com.chris64233.commodityoperations.laytime.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 装卸作业事件。事件可能重复或晚到：
 * <ul>
 *   <li>同一航次下 externalEventNo 唯一；</li>
 *   <li>同号且类型/发生时间完全相同的重放视为幂等；</li>
 *   <li>同号但内容不同返回冲突（{@code 409}）。</li>
 * </ul>
 */
@Entity
@Table(name = "operation_event",
        uniqueConstraints = @UniqueConstraint(columnNames = {"voyage_id", "external_event_no"}))
public class OperationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    @Column(name = "voyage_id", nullable = false, length = 36)
    private String voyageId;

    /** 外部事件号（上报方幂等键）。 */
    @Column(name = "external_event_no", nullable = false, length = 128)
    private String externalEventNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EventType type;

    /** 事件发生时间（业务时间线以此为准，而非接收时间）。 */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /** 系统接收时间（用于识别晚到事件）。 */
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected OperationEvent() {
    }

    public OperationEvent(String voyageId, String externalEventNo, EventType type,
                          Instant occurredAt, Instant receivedAt) {
        this.voyageId = voyageId;
        this.externalEventNo = externalEventNo;
        this.type = type;
        this.occurredAt = occurredAt;
        this.receivedAt = receivedAt;
    }

    public String getId() {
        return id;
    }

    public String getVoyageId() {
        return voyageId;
    }

    public String getExternalEventNo() {
        return externalEventNo;
    }

    public EventType getType() {
        return type;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
