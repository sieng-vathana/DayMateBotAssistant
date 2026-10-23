package com.daymate.bot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "user_settings")
public class UserSettingsEntity {
    @Id
    private Long telegramUserId;

    @Column(nullable = false, length = 8)
    private String language;

    private Instant updatedAt;

    protected UserSettingsEntity() {
    }

    public UserSettingsEntity(Long telegramUserId, String language, Instant updatedAt) {
        this.telegramUserId = telegramUserId;
        this.language = language;
        this.updatedAt = updatedAt;
    }

    public String getLanguage() {
        return language;
    }
}
