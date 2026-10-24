package com.daymate.bot.core;

import com.daymate.bot.config.BotProperties;
import com.daymate.bot.core.access.AccessAdminService;
import com.daymate.bot.core.access.AccessDeniedException;
import com.daymate.bot.core.access.AccessSeedRunner;
import com.daymate.bot.core.module.ModuleRegistry;
import com.daymate.bot.entity.AccessAuditEntity;
import com.daymate.bot.entity.FeatureSettingEntity;
import com.daymate.bot.entity.ModuleSettingEntity;
import com.daymate.bot.modules.overtime.OvertimeModule;
import com.daymate.bot.modules.task.TasksModule;
import com.daymate.bot.repository.AccessAuditRepository;
import com.daymate.bot.repository.FeatureSettingRepository;
import com.daymate.bot.repository.ModuleSettingRepository;
import com.daymate.bot.repository.UserFeatureAccessRepository;
import com.daymate.bot.repository.UserModuleAccessRepository;
import com.daymate.bot.router.ConversationHandler;
import com.daymate.bot.router.OtHandler;
import com.daymate.bot.save.TaskSaveService;
import com.daymate.bot.service.ConversationService;
import com.daymate.bot.service.PlannerFormatter;
import com.daymate.bot.telegram.TelegramApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessAdminAndSeedTest {
    @Mock
    private ModuleSettingRepository modules;
    @Mock
    private FeatureSettingRepository features;
    @Mock
    private UserModuleAccessRepository userModules;
    @Mock
    private UserFeatureAccessRepository userFeatures;
    @Mock
    private AccessAuditRepository audit;
    @Mock
    private OtHandler otHandler;
    @Mock
    private TelegramApiClient telegram;
    @Mock
    private TaskSaveService tasks;
    @Mock
    private ConversationHandler conversationHandler;
    @Mock
    private ConversationService conversations;

    private ModuleRegistry registry;
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-24T01:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        registry = new ModuleRegistry(List.of(
                new OvertimeModule(otHandler, telegram),
                new TasksModule(tasks, conversationHandler, conversations, new PlannerFormatter(), telegram)));
    }

    @Test
    void nonAdminCannotChangeAccess() {
        AccessAdminService admin = adminService("7");

        assertThatThrownBy(() -> admin.enableModule(8L, "overtime"))
                .isInstanceOf(AccessDeniedException.class);
        verify(modules, never()).save(any());
        verify(audit, never()).save(any());
    }

    @Test
    void adminEnableWritesTheRowAndAnAudit() {
        when(modules.findById("overtime")).thenReturn(Optional.empty());
        AccessAdminService admin = adminService("7");

        admin.enableModule(7L, "overtime");

        ArgumentCaptor<ModuleSettingEntity> row = ArgumentCaptor.forClass(ModuleSettingEntity.class);
        verify(modules).save(row.capture());
        assertThat(row.getValue().getModuleKey()).isEqualTo("overtime");
        assertThat(row.getValue().isEnabled()).isTrue();
        ArgumentCaptor<AccessAuditEntity> recorded = ArgumentCaptor.forClass(AccessAuditEntity.class);
        verify(audit).save(recorded.capture());
        assertThat(recorded.getValue()).extracting("actor", "action", "target")
                .containsExactly("7", "enable-module", "overtime");
    }

    @Test
    void seedInsertsMissingRowsAndLeavesAnExistingDisabledModule() {
        when(modules.findById("overtime")).thenReturn(Optional.of(
                new ModuleSettingEntity("overtime", false, 10, Instant.EPOCH)));
        when(modules.findById("tasks")).thenReturn(Optional.empty());
        when(features.findById(anyString())).thenReturn(Optional.of(
                new FeatureSettingEntity("existing", "overtime", false, Instant.EPOCH)));
        AccessSeedRunner runner = new AccessSeedRunner(registry, modules, features, clock);

        runner.run(null);

        ArgumentCaptor<ModuleSettingEntity> saved = ArgumentCaptor.forClass(ModuleSettingEntity.class);
        verify(modules).save(saved.capture());
        assertThat(saved.getValue().getModuleKey()).isEqualTo("tasks");
        assertThat(saved.getValue().isEnabled()).isFalse();
        verify(features, never()).save(any());
    }

    @Test
    void seedEnablesOnlyOpenFeaturesOfADefaultModule() {
        when(modules.findById(anyString())).thenReturn(Optional.empty());
        when(features.findById(anyString())).thenReturn(Optional.empty());
        AccessSeedRunner runner = new AccessSeedRunner(registry, modules, features, clock);

        runner.run(null);

        ArgumentCaptor<ModuleSettingEntity> savedModules = ArgumentCaptor.forClass(ModuleSettingEntity.class);
        verify(modules, org.mockito.Mockito.times(2)).save(savedModules.capture());
        assertThat(savedModules.getAllValues())
                .anySatisfy(row -> {
                    assertThat(row.getModuleKey()).isEqualTo("overtime");
                    assertThat(row.isEnabled()).isTrue();
                })
                .anySatisfy(row -> {
                    assertThat(row.getModuleKey()).isEqualTo("tasks");
                    assertThat(row.isEnabled()).isFalse();
                });
        ArgumentCaptor<FeatureSettingEntity> savedFeatures = ArgumentCaptor.forClass(FeatureSettingEntity.class);
        verify(features, org.mockito.Mockito.atLeastOnce()).save(savedFeatures.capture());
        assertThat(savedFeatures.getAllValues())
                .anySatisfy(row -> {
                    assertThat(row.getFeatureKey()).isEqualTo("ot.record");
                    assertThat(row.isEnabled()).isTrue();
                })
                .anySatisfy(row -> {
                    assertThat(row.getFeatureKey()).isEqualTo("tasks.today");
                    assertThat(row.isEnabled()).isFalse();
                });
    }

    private AccessAdminService adminService(String adminIds) {
        BotProperties properties = new BotProperties();
        properties.setAdminUserIds(adminIds);
        return new AccessAdminService(properties, registry, modules, features, userModules, userFeatures, audit, clock);
    }
}
