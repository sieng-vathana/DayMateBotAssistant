package com.daymate.bot.repository;

import com.daymate.bot.entity.LinkCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LinkCodeRepository extends JpaRepository<LinkCodeEntity, String> {
    void deleteByTelegramUserId(Long telegramUserId);
}
