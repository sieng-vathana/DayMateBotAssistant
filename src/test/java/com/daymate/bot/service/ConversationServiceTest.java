package com.daymate.bot.service;

import com.daymate.bot.core.module.BotContext;
import com.daymate.bot.core.module.BotContexts;
import com.daymate.bot.domain.ConversationStep;
import com.daymate.bot.entity.ConversationStateEntity;
import com.daymate.bot.repository.ConversationStateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-24T10:00:00Z");

    @Mock
    private ConversationStateRepository repository;

    @AfterEach
    void clearContext() {
        BotContexts.clear();
    }

    @Test
    void nullExpiryUsesUpdatedAtPlusThirtyMinutes() {
        ConversationStateEntity state = new ConversationStateEntity(
                1L, 2L, ConversationStep.OT_TIME, NOW.minusSeconds(31 * 60));
        when(repository.findById(1L)).thenReturn(Optional.of(state));

        ConversationLookup lookup = service().read(1L);

        assertThat(lookup.kind()).isEqualTo(ConversationLookup.Kind.EXPIRED);
        verify(repository).deleteById(1L);
    }

    @Test
    void aFreshRowStays() {
        ConversationStateEntity state = new ConversationStateEntity(
                1L, 2L, ConversationStep.OT_TIME, NOW.minusSeconds(10 * 60));
        when(repository.findById(1L)).thenReturn(Optional.of(state));

        ConversationLookup lookup = service().read(1L);

        assertThat(lookup.kind()).isEqualTo(ConversationLookup.Kind.FOUND);
        assertThat(lookup.state()).isSameAs(state);
        verify(repository, never()).deleteById(1L);
    }

    @Test
    void saveStampsModuleFromTheContextAndDoesNotOverwriteIt() {
        BotContexts.set(new BotContext(1L, 2L, null, null, null, "overtime", "ot.record", Set.of("ot.record")));
        ConversationStateEntity state = new ConversationStateEntity(1L, 2L, ConversationStep.TITLE, NOW);
        ConversationService service = service();

        service.save(state);

        assertThat(state.getModuleKey()).isEqualTo("overtime");
        assertThat(state.getAction()).isEqualTo("ot.record");
        assertThat(state.getExpiresAt()).isEqualTo(NOW.plus(ConversationService.IDLE));
        state.setModuleKey("tasks");
        service.save(state);
        assertThat(state.getModuleKey()).isEqualTo("tasks");
    }

    @Test
    void saveWithoutContextUsesTheStep() {
        ConversationStateEntity salary = new ConversationStateEntity(1L, 2L, ConversationStep.BASE_SALARY, NOW);
        ConversationStateEntity title = new ConversationStateEntity(3L, 2L, ConversationStep.TITLE, NOW);
        ConversationService service = service();

        service.save(salary);
        service.save(title);

        assertThat(salary.getModuleKey()).isEqualTo("overtime");
        assertThat(title.getModuleKey()).isEqualTo("tasks");
    }

    private ConversationService service() {
        return new ConversationService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
