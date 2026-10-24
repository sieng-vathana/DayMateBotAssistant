package com.daymate.bot.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "handled_updates")
public class HandledUpdateEntity {
    @Id
    private Long updateId;

    private Instant handledAt;

    protected HandledUpdateEntity() {
    }

    public HandledUpdateEntity(Long updateId, Instant handledAt) {
        this.updateId = updateId;
        this.handledAt = handledAt;
    }

    public Instant getHandledAt() {
        return handledAt;
    }
}
