package com.daymate.bot.repository;

import com.daymate.bot.entity.UserFeatureAccessEntity;
import com.daymate.bot.entity.UserFeatureAccessId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserFeatureAccessRepository extends JpaRepository<UserFeatureAccessEntity, UserFeatureAccessId> {
}
