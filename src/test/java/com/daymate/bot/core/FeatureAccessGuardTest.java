package com.daymate.bot.core;

import com.daymate.bot.core.access.AccessDecision;
import com.daymate.bot.core.access.FeatureAccessGuard;
import com.daymate.bot.core.module.ModuleRegistry;
import com.daymate.bot.entity.FeatureSettingEntity;
import com.daymate.bot.entity.ModuleSettingEntity;
import com.daymate.bot.entity.UserFeatureAccessEntity;
import com.daymate.bot.entity.UserFeatureAccessId;
import com.daymate.bot.entity.UserModuleAccessEntity;
import com.daymate.bot.entity.UserModuleAccessId;
import com.daymate.bot.modules.overtime.OvertimeModule;
import com.daymate.bot.modules.task.TasksModule;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeatureAccessGuardTest {
    @Mock
    private ModuleSettingRepository modules;
    @Mock
    private FeatureSettingRepository features;
    @Mock
    private UserModuleAccessRepository userModules;
    @Mock
    private UserFeatureAccessRepository userFeatures;
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

    private FeatureAccessGuard guard;

    @BeforeEach
    void setUp() {
        ModuleRegistry registry = new ModuleRegistry(List.of(
                new OvertimeModule(otHandler, telegram),
                new TasksModule(tasks, conversationHandler, conversations, new PlannerFormatter(), telegram)));
        guard = new FeatureAccessGuard(registry, modules, features, userModules, userFeatures);
    }

    @Test
    void missingModuleRowDeniesOvertime() {
        assertThat(guard.decide(1L, "overtime", "ot.record")).isEqualTo(AccessDecision.MODULE_OFF);
    }

    @Test
    void overtimeWithoutAUserRowIsAllowed() {
        enabled("overtime", 10);
        enabledFeature("ot.record", "overtime");

        assertThat(guard.decide(1L, "overtime", "ot.record")).isEqualTo(AccessDecision.ALLOW);
    }

    @Test
    void explicitDenialBeatsTheOvertimeDefault() {
        enabled("overtime", 10);
        when(userModules.findById(new UserModuleAccessId(1L, "overtime")))
                .thenReturn(Optional.of(new UserModuleAccessEntity(1L, "overtime", false, Instant.EPOCH)));

        assertThat(guard.decide(1L, "overtime", "ot.record")).isEqualTo(AccessDecision.MODULE_OFF);
    }

    @Test
    void tasksWithoutAUserRowStayDenied() {
        enabled("tasks", 20);

        assertThat(guard.decide(1L, "tasks", "tasks.today")).isEqualTo(AccessDecision.MODULE_OFF);
    }

    @Test
    void closedFeatureWithoutAUserRowIsDenied() {
        enabled("tasks", 20);
        allowModule(1L, "tasks");
        enabledFeature("tasks.today", "tasks");

        assertThat(guard.decide(1L, "tasks", "tasks.today")).isEqualTo(AccessDecision.FEATURE_OFF);
    }

    @Test
    void grantedFeatureAllowsAClosedModule() {
        enabled("tasks", 20);
        allowModule(1L, "tasks");
        enabledFeature("tasks.today", "tasks");
        when(userFeatures.findById(new UserFeatureAccessId(1L, "tasks.today")))
                .thenReturn(Optional.of(new UserFeatureAccessEntity(1L, "tasks.today", true, Instant.EPOCH)));

        assertThat(guard.decide(1L, "tasks", "tasks.today")).isEqualTo(AccessDecision.ALLOW);
    }

    @Test
    void missingFeatureRowIsDenied() {
        enabled("overtime", 10);
        assertThat(guard.decide(1L, "overtime", "ot.list")).isEqualTo(AccessDecision.FEATURE_OFF);
    }

    @Test
    void unknownModuleAndUnknownFeatureAreUnknown() {
        assertThat(guard.decide(1L, "missing", "missing.open")).isEqualTo(AccessDecision.UNKNOWN);
        enabled("overtime", 10);
        assertThat(guard.decide(1L, "overtime", "not.a.feature")).isEqualTo(AccessDecision.UNKNOWN);
    }

    private void enabled(String moduleId, int order) {
        when(modules.findById(moduleId))
                .thenReturn(Optional.of(new ModuleSettingEntity(moduleId, true, order, Instant.EPOCH)));
    }

    private void enabledFeature(String featureKey, String moduleId) {
        when(features.findById(featureKey))
                .thenReturn(Optional.of(new FeatureSettingEntity(featureKey, moduleId, true, Instant.EPOCH)));
    }

    private void allowModule(Long userId, String moduleId) {
        when(userModules.findById(new UserModuleAccessId(userId, moduleId)))
                .thenReturn(Optional.of(new UserModuleAccessEntity(userId, moduleId, true, Instant.EPOCH)));
    }
}
