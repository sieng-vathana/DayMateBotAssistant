package com.daymate.bot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "bot_state")
public class BotStateEntity {
    @Id
    @Column(length = 128)
    private String stateKey;

    @Column(length = 256)
    private String stateValue;

    private Instant updatedAt;

    protected BotStateEntity() {
    }

    public BotStateEntity(String stateKey, String stateValue, Instant updatedAt) {
        this.stateKey = stateKey;
        this.stateValue = stateValue;
        this.updatedAt = updatedAt;
    }

    public String getStateKey() {
        return stateKey;
    }

    public String getStateValue() {
        return stateValue;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
