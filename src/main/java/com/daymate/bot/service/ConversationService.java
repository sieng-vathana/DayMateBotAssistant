package com.daymate.bot.service;

import com.daymate.bot.domain.ConversationStep;
import com.daymate.bot.entity.ConversationStateEntity;
import com.daymate.bot.repository.ConversationStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
public class ConversationService {
    private final ConversationStateRepository repository;
    private final Clock clock;

    public ConversationService(ConversationStateRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<ConversationStateEntity> get(Long chatId) {
        return repository.findById(chatId);
    }

    @Transactional(readOnly = true)
    public boolean isActive(Long chatId) {
        return repository.existsById(chatId);
    }

    @Transactional
    public ConversationStateEntity start(Long chatId, Long userId) {
        ConversationStateEntity state = new ConversationStateEntity(
                chatId, userId, ConversationStep.TITLE, Instant.now(clock));
        return repository.save(state);
    }

    @Transactional
    public ConversationStateEntity save(ConversationStateEntity state) {
        state.setUpdatedAt(Instant.now(clock));
        return repository.save(state);
    }

    @Transactional
    public void clear(Long chatId) {
        repository.deleteById(chatId);
    }
}
