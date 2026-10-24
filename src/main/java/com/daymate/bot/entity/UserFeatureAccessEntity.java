package com.daymate.bot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "user_feature_access")
@IdClass(UserFeatureAccessId.class)
public class UserFeatureAccessEntity {
    @Id
    private Long userId;

    @Id
    @Column(length = 64)
    private String featureKey;

    @Column(nullable = false)
    private boolean allowed;

    private Instant updatedAt;

    protected UserFeatureAccessEntity() {
    }

    public UserFeatureAccessEntity(Long userId, String featureKey, boolean allowed, Instant updatedAt) {
        this.userId = userId;
        this.featureKey = featureKey;
        this.allowed = allowed;
        this.updatedAt = updatedAt;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
