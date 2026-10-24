package com.daymate.bot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "module_settings")
public class ModuleSettingEntity {
    @Id
    @Column(length = 32)
    private String moduleKey;

    @Column(nullable = false)
    private boolean enabled;

    private int displayOrder;
    private Instant updatedAt;

    protected ModuleSettingEntity() {
    }

    public ModuleSettingEntity(String moduleKey, boolean enabled, int displayOrder, Instant updatedAt) {
        this.moduleKey = moduleKey;
        this.enabled = enabled;
        this.displayOrder = displayOrder;
        this.updatedAt = updatedAt;
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

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
