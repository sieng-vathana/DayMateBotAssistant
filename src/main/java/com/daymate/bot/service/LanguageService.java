package com.daymate.bot.service;

import com.daymate.bot.domain.Language;
import com.daymate.bot.entity.UserSettingsEntity;
import com.daymate.bot.repository.UserSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class LanguageService {
    private final UserSettingsRepository settings;
    private final Clock clock;

    public LanguageService(UserSettingsRepository settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Language get(Long userId) {
        if (userId == null) {
            return Language.EN;
        }
        return settings.findById(userId)
                .map(row -> Language.from(row.getLanguage()))
                .orElse(Language.EN);
    }

    @Transactional
    public void set(Long userId, Language language) {
        settings.save(new UserSettingsEntity(userId, language.name(), Instant.now(clock)));
    }
}
