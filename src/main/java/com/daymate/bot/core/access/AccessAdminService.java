package com.daymate.bot.core.access;

import com.daymate.bot.config.BotProperties;
import com.daymate.bot.core.module.BotModule;
import com.daymate.bot.core.module.ModuleRegistry;
import com.daymate.bot.entity.AccessAuditEntity;
import com.daymate.bot.entity.FeatureSettingEntity;
import com.daymate.bot.entity.ModuleSettingEntity;
import com.daymate.bot.entity.UserFeatureAccessEntity;
import com.daymate.bot.entity.UserFeatureAccessId;
import com.daymate.bot.entity.UserModuleAccessEntity;
import com.daymate.bot.entity.UserModuleAccessId;
import com.daymate.bot.repository.AccessAuditRepository;
import com.daymate.bot.repository.FeatureSettingRepository;
import com.daymate.bot.repository.ModuleSettingRepository;
import com.daymate.bot.repository.UserFeatureAccessRepository;
import com.daymate.bot.repository.UserModuleAccessRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class AccessAdminService {
    private final BotProperties properties;
    private final ModuleRegistry registry;
    private final ModuleSettingRepository modules;
    private final FeatureSettingRepository features;
    private final UserModuleAccessRepository userModules;
    private final UserFeatureAccessRepository userFeatures;
    private final AccessAuditRepository audit;
    private final Clock clock;

    public AccessAdminService(BotProperties properties, ModuleRegistry registry, ModuleSettingRepository modules,
                              FeatureSettingRepository features, UserModuleAccessRepository userModules,
                              UserFeatureAccessRepository userFeatures, AccessAuditRepository audit, Clock clock) {
        this.properties = properties;
        this.registry = registry;
        this.modules = modules;
        this.features = features;
        this.userModules = userModules;
        this.userFeatures = userFeatures;
        this.audit = audit;
        this.clock = clock;
        properties.adminUserIds();
    }

    @Transactional
    public void enableModule(Long actorUserId, String moduleKey) {
        setModule(actorUserId, moduleKey, true);
    }

    @Transactional
    public void disableModule(Long actorUserId, String moduleKey) {
        setModule(actorUserId, moduleKey, false);
    }

    @Transactional
    public void enableFeature(Long actorUserId, String featureKey) {
        setFeature(actorUserId, featureKey, true);
    }

    @Transactional
    public void disableFeature(Long actorUserId, String featureKey) {
        setFeature(actorUserId, featureKey, false);
    }

    @Transactional
    public void grantModuleAccess(Long actorUserId, Long userId, String moduleKey) {
        setUserModule(actorUserId, userId, moduleKey, true);
    }

    @Transactional
    public void revokeModuleAccess(Long actorUserId, Long userId, String moduleKey) {
        setUserModule(actorUserId, userId, moduleKey, false);
    }

    @Transactional
    public void grantFeatureAccess(Long actorUserId, Long userId, String featureKey) {
        setUserFeature(actorUserId, userId, featureKey, true);
    }

    @Transactional
    public void revokeFeatureAccess(Long actorUserId, Long userId, String featureKey) {
        setUserFeature(actorUserId, userId, featureKey, false);
    }

    private void setModule(Long actorUserId, String moduleKey, boolean enabled) {
        requireAdmin(actorUserId);
        requireModule(moduleKey);
        Instant now = Instant.now(clock);
        ModuleSettingEntity row = modules.findById(moduleKey)
                .orElseGet(() -> new ModuleSettingEntity(moduleKey, enabled, 0, now));
        row.setEnabled(enabled);
        row.setUpdatedAt(now);
        modules.save(row);
        audit(actorUserId, enabled ? "enable-module" : "disable-module", moduleKey, now);
    }

    private void setFeature(Long actorUserId, String featureKey, boolean enabled) {
        requireAdmin(actorUserId);
        BotModule owner = ownerOf(featureKey);
        Instant now = Instant.now(clock);
        FeatureSettingEntity row = features.findById(featureKey)
                .orElseGet(() -> new FeatureSettingEntity(featureKey, owner.moduleId(), enabled, now));
        row.setEnabled(enabled);
        row.setUpdatedAt(now);
        features.save(row);
        audit(actorUserId, enabled ? "enable-feature" : "disable-feature", featureKey, now);
    }

    private void setUserModule(Long actorUserId, Long userId, String moduleKey, boolean allowed) {
        requireAdmin(actorUserId);
        requireModule(moduleKey);
        requireUser(userId);
        Instant now = Instant.now(clock);
        UserModuleAccessEntity row = userModules.findById(new UserModuleAccessId(userId, moduleKey))
                .orElseGet(() -> new UserModuleAccessEntity(userId, moduleKey, allowed, now));
        row.setAllowed(allowed);
        row.setUpdatedAt(now);
        userModules.save(row);
        audit(actorUserId, allowed ? "grant-module" : "revoke-module", userId + ":" + moduleKey, now);
    }

    private void setUserFeature(Long actorUserId, Long userId, String featureKey, boolean allowed) {
        requireAdmin(actorUserId);
        ownerOf(featureKey);
        requireUser(userId);
        Instant now = Instant.now(clock);
        UserFeatureAccessEntity row = userFeatures.findById(new UserFeatureAccessId(userId, featureKey))
                .orElseGet(() -> new UserFeatureAccessEntity(userId, featureKey, allowed, now));
        row.setAllowed(allowed);
        row.setUpdatedAt(now);
        userFeatures.save(row);
        audit(actorUserId, allowed ? "grant-feature" : "revoke-feature", userId + ":" + featureKey, now);
    }

    private void requireAdmin(Long actorUserId) {
        if (actorUserId == null || !properties.adminUserIds().contains(actorUserId)) {
            throw new AccessDeniedException("Administrator authorization is required.");
        }
    }

    private void requireModule(String moduleKey) {
        if (registry.find(moduleKey).isEmpty()) {
            throw new IllegalArgumentException("Unknown module.");
        }
    }

    private BotModule ownerOf(String featureKey) {
        return registry.all().stream()
                .filter(module -> module.features().stream().anyMatch(feature -> feature.key().equals(featureKey)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown feature."));
    }

    private static void requireUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("A Telegram user id is required.");
        }
    }

    private void audit(Long actorUserId, String action, String target, Instant now) {
        audit.save(new AccessAuditEntity(Long.toString(actorUserId), action, target, now));
    }
}
