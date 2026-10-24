package com.daymate.bot.core.access;

import com.daymate.bot.core.module.BotModule;
import com.daymate.bot.core.module.FeatureDeclaration;
import com.daymate.bot.core.module.ModuleRegistry;
import com.daymate.bot.entity.FeatureSettingEntity;
import com.daymate.bot.entity.ModuleSettingEntity;
import com.daymate.bot.repository.FeatureSettingRepository;
import com.daymate.bot.repository.ModuleSettingRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Component
public class AccessSeedRunner implements ApplicationRunner {
    private final ModuleRegistry registry;
    private final ModuleSettingRepository modules;
    private final FeatureSettingRepository features;
    private final Clock clock;

    public AccessSeedRunner(ModuleRegistry registry, ModuleSettingRepository modules,
                            FeatureSettingRepository features, Clock clock) {
        this.registry = registry;
        this.modules = modules;
        this.features = features;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Instant now = Instant.now(clock);
        for (BotModule module : registry.all()) {
            if (modules.findById(module.moduleId()).isEmpty()) {
                modules.save(new ModuleSettingEntity(
                        module.moduleId(), module.enabledByDefault(), module.displayOrder(), now));
            }
            for (FeatureDeclaration feature : module.features()) {
                if (features.findById(feature.key()).isEmpty()) {
                    boolean enabled = module.enabledByDefault() && feature.openByDefault();
                    features.save(new FeatureSettingEntity(feature.key(), module.moduleId(), enabled, now));
                }
            }
        }
    }
}
