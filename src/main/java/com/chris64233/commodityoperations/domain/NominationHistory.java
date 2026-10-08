package com.chris64233.commodityoperations.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "nomination_history", indexes =
        @Index(name = "idx_history_nomination", columnList = "nomination_id"))
public class NominationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nomination_id", nullable = false)
    private Long nominationId;

    @Column(nullable = false, length = 32)
    private String action;

    @Lob
    @Column(length = 4000)
    private String detail;

    @Column(nullable = false)
    private LocalDateTime eventTime;

    protected NominationHistory() {
    }

    public NominationHistory(Long nominationId, String action, String detail) {
        this.nominationId = nominationId;
        this.action = action;
        this.detail = detail;
        this.eventTime = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getNominationId() {
        return nominationId;
    }

    public String getAction() {
        return action;
    }

    public String getDetail() {
        return detail;
    }

    public LocalDateTime getEventTime() {
        return eventTime;
    }
}
