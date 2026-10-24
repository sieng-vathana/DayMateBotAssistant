package com.daymate.bot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "feature_settings")
public class FeatureSettingEntity {
    @Id
    @Column(length = 64)
    private String featureKey;

    @Column(nullable = false, length = 32)
    private String moduleKey;

    @Column(nullable = false)
    private boolean enabled;

    private Instant updatedAt;

    protected FeatureSettingEntity() {
    }

    public FeatureSettingEntity(String featureKey, String moduleKey, boolean enabled, Instant updatedAt) {
        this.featureKey = featureKey;
        this.moduleKey = moduleKey;
        this.enabled = enabled;
        this.updatedAt = updatedAt;
    }

    public String getFeatureKey() {
        return featureKey;
    }

    public String getModuleKey() {
        return moduleKey;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
