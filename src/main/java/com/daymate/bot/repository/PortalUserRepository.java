package com.daymate.bot.repository;

import com.daymate.bot.entity.PortalUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PortalUserRepository extends JpaRepository<PortalUserEntity, Long> {
    Optional<PortalUserEntity> findByUsername(String username);

    Optional<PortalUserEntity> findByTelegramUserId(Long telegramUserId);
}
