package com.chris64233.commodityoperations.vesselnomination;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "nomination_event")
public class NominationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long nominationId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false, length = 1000)
    private String detail;

    @Column(nullable = false)
    private Instant occurredAt;

    protected NominationEvent() {
    }

    public NominationEvent(Long nominationId, String eventType, String detail) {
        this.nominationId = nominationId;
        this.eventType = eventType;
        this.detail = detail;
        this.occurredAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getNominationId() {
        return nominationId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
