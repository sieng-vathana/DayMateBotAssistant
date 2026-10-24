package com.daymate.bot.entity;

import java.io.Serializable;
import java.util.Objects;

public class UserFeatureAccessId implements Serializable {
    private Long userId;
    private String featureKey;

    public UserFeatureAccessId() {
    }

    public UserFeatureAccessId(Long userId, String featureKey) {
        this.userId = userId;
        this.featureKey = featureKey;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UserFeatureAccessId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(featureKey, that.featureKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, featureKey);
    }
}
