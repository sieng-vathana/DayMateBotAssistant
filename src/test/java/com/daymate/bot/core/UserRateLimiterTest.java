package com.daymate.bot.core;

import com.daymate.bot.core.security.UpdateDeduper;
import com.daymate.bot.core.security.UserRateLimiter;
import com.daymate.bot.repository.HandledUpdateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRateLimiterTest {
    @Mock
    private HandledUpdateRepository updates;

    @Test
    void theThirtyFirstUpdateInsideTheWindowIsRejected() {
        UserRateLimiter limiter = new UserRateLimiter();

        for (int i = 0; i < 30; i++) {
            assertThat(limiter.allow(5L)).isTrue();
        }
        assertThat(limiter.allow(5L)).isFalse();
        assertThat(limiter.allow(6L)).isTrue();
    }

    @Test
    void aRepeatedUpdateIdIsAlreadyHandled() {
        when(updates.existsById(9L)).thenReturn(false, false, true);
        UpdateDeduper deduper = new UpdateDeduper(updates, Clock.fixed(Instant.parse("2026-10-24T01:00:00Z"), ZoneOffset.UTC));

        assertThat(deduper.alreadyHandled(9L)).isFalse();
        deduper.markHandled(9L);
        assertThat(deduper.alreadyHandled(9L)).isTrue();
        verify(updates).save(org.mockito.ArgumentMatchers.any());
    }
}
