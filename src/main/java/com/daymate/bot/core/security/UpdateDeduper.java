package com.daymate.bot.core.security;

import com.daymate.bot.entity.HandledUpdateEntity;
import com.daymate.bot.repository.HandledUpdateRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class UpdateDeduper {
    private final HandledUpdateRepository updates;
    private final Clock clock;

    public UpdateDeduper(HandledUpdateRepository updates, Clock clock) {
        this.updates = updates;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean alreadyHandled(long updateId) {
        return updates.existsById(updateId);
    }

    @Transactional
    public void markHandled(long updateId) {
        if (!updates.existsById(updateId)) {
            updates.save(new HandledUpdateEntity(updateId, Instant.now(clock)));
        }
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "${telegram.bot.timezone:Asia/Phnom_Penh}")
    @Transactional
    public void purgeOldUpdates() {
        updates.deleteByHandledAtBefore(Instant.now(clock).minus(Duration.ofDays(2)));
    }
}
