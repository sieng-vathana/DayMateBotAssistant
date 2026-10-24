package com.daymate.bot.core.access;

import com.daymate.bot.core.module.BotModule;
import com.daymate.bot.core.module.FeatureDeclaration;
import com.daymate.bot.core.module.ModuleRegistry;
import com.daymate.bot.entity.FeatureSettingEntity;
import com.daymate.bot.entity.ModuleSettingEntity;
import com.daymate.bot.entity.UserFeatureAccessEntity;
import com.daymate.bot.entity.UserFeatureAccessId;
import com.daymate.bot.entity.UserModuleAccessEntity;
import com.daymate.bot.entity.UserModuleAccessId;
import com.daymate.bot.repository.FeatureSettingRepository;
import com.daymate.bot.repository.ModuleSettingRepository;
import com.daymate.bot.repository.UserFeatureAccessRepository;
import com.daymate.bot.repository.UserModuleAccessRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class FeatureAccessGuard {
    public static final String OVERTIME = "overtime";
    public static final String NOTES = "notes";

    private final ModuleRegistry registry;
    private final ModuleSettingRepository modules;
    private final FeatureSettingRepository features;
    private final UserModuleAccessRepository userModules;
    private final UserFeatureAccessRepository userFeatures;

    public FeatureAccessGuard(ModuleRegistry registry, ModuleSettingRepository modules,
                              FeatureSettingRepository features, UserModuleAccessRepository userModules,
                              UserFeatureAccessRepository userFeatures) {
        this.registry = registry;
        this.modules = modules;
        this.features = features;
        this.userModules = userModules;
        this.userFeatures = userFeatures;
    }

    public AccessDecision decide(Long userId, String moduleId, String featureKey) {
        Optional<BotModule> module = registry.find(moduleId);
        if (module.isEmpty()) {
            return AccessDecision.UNKNOWN;
        }
        if (!moduleEnabled(moduleId)) {
            return AccessDecision.MODULE_OFF;
        }
        if (!userMayUseModule(userId, moduleId)) {
            return AccessDecision.MODULE_OFF;
        }
        if (featureKey == null || featureKey.isBlank()) {
            return AccessDecision.ALLOW;
        }
        if (module.get().features().stream().noneMatch(feature -> feature.key().equals(featureKey))) {
            return AccessDecision.UNKNOWN;
        }
        if (!featureEnabled(moduleId, featureKey)) {
            return AccessDecision.FEATURE_OFF;
        }
        if (!userMayUseFeature(module.get(), userId, featureKey)) {
            return AccessDecision.FEATURE_OFF;
        }
        return AccessDecision.ALLOW;
    }

    public boolean isModuleEnabled(String moduleId) {
        return moduleEnabled(moduleId);
    }

    public boolean visible(Long userId, BotModule module) {
        if (decide(userId, module.moduleId(), null) != AccessDecision.ALLOW) {
            return false;
        }
        return module.features().stream()
                .anyMatch(feature -> decide(userId, module.moduleId(), feature.key()) == AccessDecision.ALLOW);
    }

    private boolean moduleEnabled(String moduleId) {
        return modules.findById(moduleId).map(ModuleSettingEntity::isEnabled).orElse(false);
    }

    private boolean featureEnabled(String moduleId, String featureKey) {
        Optional<FeatureSettingEntity> row = features.findById(featureKey);
        return row.isPresent() && row.get().isEnabled() && moduleId.equals(row.get().getModuleKey());
    }

    private boolean userMayUseModule(Long userId, String moduleId) {
        Optional<UserModuleAccessEntity> row = userModules.findById(new UserModuleAccessId(userId, moduleId));
        if (row.isPresent()) {
            return row.get().isAllowed();
        }
        return OVERTIME.equals(moduleId) || NOTES.equals(moduleId);
    }

    private boolean userMayUseFeature(BotModule module, Long userId, String featureKey) {
        Optional<UserFeatureAccessEntity> row = userFeatures.findById(new UserFeatureAccessId(userId, featureKey));
        if (row.isPresent()) {
            return row.get().isAllowed();
        }
        return module.features().stream()
                .filter(feature -> feature.key().equals(featureKey))
                .findFirst()
                .map(FeatureDeclaration::openByDefault)
                .orElse(false);
    }
}
