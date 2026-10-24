package com.daymate.bot.entity;

import java.io.Serializable;
import java.util.Objects;

public class UserModuleAccessId implements Serializable {
    private Long userId;
    private String moduleKey;

    public UserModuleAccessId() {
    }

    public UserModuleAccessId(Long userId, String moduleKey) {
        this.userId = userId;
        this.moduleKey = moduleKey;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UserModuleAccessId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(moduleKey, that.moduleKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, moduleKey);
    }
}
