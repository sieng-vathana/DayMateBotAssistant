package com.daymate.bot.repository;

import com.daymate.bot.entity.HandledUpdateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface HandledUpdateRepository extends JpaRepository<HandledUpdateEntity, Long> {
    void deleteByHandledAtBefore(Instant cutoff);
}
