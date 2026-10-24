package com.daymate.bot.repository;

import com.daymate.bot.entity.PortalSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortalSessionRepository extends JpaRepository<PortalSessionEntity, String> {
}
