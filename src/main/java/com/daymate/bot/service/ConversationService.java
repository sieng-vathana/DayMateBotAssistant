package com.daymate.bot.service;

import com.daymate.bot.core.module.BotContext;
import com.daymate.bot.core.module.BotContexts;
import com.daymate.bot.domain.ConversationStep;
import com.daymate.bot.entity.ConversationStateEntity;
import com.daymate.bot.repository.ConversationStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class ConversationService {
    static final Duration IDLE = Duration.ofMinutes(30);

    private final ConversationStateRepository repository;
    private final Clock clock;

    public ConversationService(ConversationStateRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public Optional<ConversationStateEntity> get(Long chatId) {
        ConversationLookup lookup = load(chatId);
        if (lookup.kind() == ConversationLookup.Kind.FOUND) {
            return Optional.of(lookup.state());
        }
        return Optional.empty();
    }

    @Transactional
    public ConversationLookup read(Long chatId) {
        return load(chatId);
    }

    @Transactional
    public boolean isActive(Long chatId) {
        return load(chatId).kind() == ConversationLookup.Kind.FOUND;
    }

    @Transactional
    public ConversationStateEntity start(Long chatId, Long userId) {
        ConversationStateEntity state = new ConversationStateEntity(
                chatId, userId, ConversationStep.TITLE, Instant.now(clock));
        return save(state);
    }

    @Transactional
    public ConversationStateEntity save(ConversationStateEntity state) {
        Instant now = Instant.now(clock);
        state.setUpdatedAt(now);
        state.setExpiresAt(now.plus(IDLE));
        stamp(state);
        return repository.save(state);
    }

    @Transactional
    public void clear(Long chatId) {
        repository.deleteById(chatId);
    }

    private ConversationLookup load(Long chatId) {
        Optional<ConversationStateEntity> found = repository.findById(chatId);
        if (found.isEmpty()) {
            return ConversationLookup.none();
        }
        ConversationStateEntity state = found.get();
        Instant expiry = state.getExpiresAt();
        if (expiry == null && state.getUpdatedAt() != null) {
            expiry = state.getUpdatedAt().plus(IDLE);
        }
        if (expiry != null && !expiry.isAfter(Instant.now(clock))) {
            repository.deleteById(chatId);
            return ConversationLookup.expired();
        }
        return ConversationLookup.found(state);
    }

    private static void stamp(ConversationStateEntity state) {
        BotContext current = BotContexts.current();
        if (state.getModuleKey() == null) {
            if (current != null && current.moduleId() != null) {
                state.setModuleKey(current.moduleId());
            } else if (state.getStep().isOtSetup()) {
                state.setModuleKey("overtime");
            } else {
                state.setModuleKey("tasks");
            }
        }
        if (state.getAction() == null && current != null && current.featureKey() != null) {
            state.setAction(current.featureKey());
        }
    }
}
