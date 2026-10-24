package com.daymate.bot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "portal_link_codes")
public class LinkCodeEntity {
    @Id
    @Column(length = 8)
    private String code;

    @Column(nullable = false)
    private Long telegramUserId;

    @Column(nullable = false)
    private Instant expiresAt;

    protected LinkCodeEntity() {
    }

    public LinkCodeEntity(String code, Long telegramUserId, Instant expiresAt) {
        this.code = code;
        this.telegramUserId = telegramUserId;
        this.expiresAt = expiresAt;
    }

    public Long getTelegramUserId() {
        return telegramUserId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
