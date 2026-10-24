package com.daymate.bot.repository;

import com.daymate.bot.entity.FeatureSettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeatureSettingRepository extends JpaRepository<FeatureSettingEntity, String> {
}
