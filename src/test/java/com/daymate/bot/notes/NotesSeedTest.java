package com.daymate.bot.notes;

import com.daymate.bot.core.access.AccessSeedRunner;
import com.daymate.bot.core.module.ModuleRegistry;
import com.daymate.bot.entity.FeatureSettingEntity;
import com.daymate.bot.entity.ModuleSettingEntity;
import com.daymate.bot.modules.notes.NotesModule;
import com.daymate.bot.repository.FeatureSettingRepository;
import com.daymate.bot.repository.ModuleSettingRepository;
import com.daymate.bot.service.ConversationService;
import com.daymate.bot.telegram.TelegramApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotesSeedTest {
    @Mock
    private ModuleSettingRepository modules;
    @Mock
    private FeatureSettingRepository features;
    @Mock
    private NoteService noteService;
    @Mock
    private ConversationService conversations;
    @Mock
    private TelegramApiClient telegram;

    private ModuleRegistry registry;
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-24T01:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        registry = new ModuleRegistry(java.util.List.of(new NotesModule(
                noteService, conversations, telegram,
                new NoteFormatter(ZoneId.of("Asia/Phnom_Penh")),
                new NoteTimes(clock, ZoneId.of("Asia/Phnom_Penh")))));
    }

    @Test
    void seedEnablesNotesAndItsOpenFeatures() {
        when(modules.findById(anyString())).thenReturn(Optional.empty());
        when(features.findById(anyString())).thenReturn(Optional.empty());
        AccessSeedRunner runner = new AccessSeedRunner(registry, modules, features, clock);

        runner.run(null);

        ArgumentCaptor<ModuleSettingEntity> savedModules = ArgumentCaptor.forClass(ModuleSettingEntity.class);
        verify(modules).save(savedModules.capture());
        assertThat(savedModules.getValue().getModuleKey()).isEqualTo("notes");
        assertThat(savedModules.getValue().isEnabled()).isTrue();
        ArgumentCaptor<FeatureSettingEntity> savedFeatures = ArgumentCaptor.forClass(FeatureSettingEntity.class);
        verify(features, org.mockito.Mockito.atLeastOnce()).save(savedFeatures.capture());
        assertThat(savedFeatures.getAllValues())
                .anySatisfy(row -> {
                    assertThat(row.getFeatureKey()).isEqualTo("notes.create");
                    assertThat(row.isEnabled()).isTrue();
                })
                .anySatisfy(row -> {
                    assertThat(row.getFeatureKey()).isEqualTo("notes.delete");
                    assertThat(row.isEnabled()).isTrue();
                });
    }

    @Test
    void seedDoesNotReenableADisabledNotesModule() {
        when(modules.findById("notes")).thenReturn(Optional.of(
                new ModuleSettingEntity("notes", false, 40, Instant.EPOCH)));
        when(features.findById(anyString())).thenReturn(Optional.of(
                new FeatureSettingEntity("notes.create", "notes", true, Instant.EPOCH)));
        AccessSeedRunner runner = new AccessSeedRunner(registry, modules, features, clock);

        runner.run(null);

        verify(modules, never()).save(any());
        verify(features, never()).save(any());
    }
}
