package com.daymate.bot.repository;

import com.daymate.bot.entity.BotStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BotStateRepository extends JpaRepository<BotStateEntity, String> {
}
