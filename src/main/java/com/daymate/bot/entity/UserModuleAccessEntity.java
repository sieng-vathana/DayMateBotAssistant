package com.daymate.bot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "user_module_access")
@IdClass(UserModuleAccessId.class)
public class UserModuleAccessEntity {
    @Id
    private Long userId;

    @Id
    @Column(length = 32)
    private String moduleKey;

    @Column(nullable = false)
    private boolean allowed;

    private Instant updatedAt;

    protected UserModuleAccessEntity() {
    }

    public UserModuleAccessEntity(Long userId, String moduleKey, boolean allowed, Instant updatedAt) {
        this.userId = userId;
        this.moduleKey = moduleKey;
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
