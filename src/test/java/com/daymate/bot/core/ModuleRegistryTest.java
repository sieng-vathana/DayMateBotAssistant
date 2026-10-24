package com.daymate.bot.core;

import com.daymate.bot.core.module.BotContext;
import com.daymate.bot.core.module.BotModule;
import com.daymate.bot.core.module.FeatureDeclaration;
import com.daymate.bot.core.module.MenuAction;
import com.daymate.bot.core.module.ModuleRegistry;
import com.daymate.bot.entity.ConversationStateEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModuleRegistryTest {
    @Test
    void duplicateModuleIdIsRejected() {
        assertThatThrownBy(() -> new ModuleRegistry(List.of(module("overtime", "ot"), module("overtime", "ot2"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Duplicate module id: overtime");
    }

    @Test
    void longestCallbackPrefixWins() {
        ModuleRegistry registry = new ModuleRegistry(List.of(module("short", "ot"), module("long", "otedit")));

        assertThat(registry.findByCallback("ot:edittime:1")).get().extracting(BotModule::moduleId).isEqualTo("short");
        assertThat(registry.findByCallback("ot:edit:1")).get().extracting(BotModule::moduleId).isEqualTo("short");
        assertThat(registry.findByCallback("otedit:1")).get().extracting(BotModule::moduleId).isEqualTo("long");
        assertThat(registry.findByCallback("evil:run")).isEmpty();
    }

    private static BotModule module(String id, String prefix) {
        return new BotModule() {
            @Override
            public String moduleId() {
                return id;
            }

            @Override
            public String displayName() {
                return id;
            }

            @Override
            public String description() {
                return "";
            }

            @Override
            public int displayOrder() {
                return 0;
            }

            @Override
            public boolean enabledByDefault() {
                return false;
            }

            @Override
            public List<String> commands() {
                return List.of();
            }

            @Override
            public List<String> callbackPrefixes() {
                return List.of(prefix);
            }

            @Override
            public List<FeatureDeclaration> features() {
                return List.of();
            }

            @Override
            public List<MenuAction> menuActions() {
                return List.of();
            }

            @Override
            public String featureForCommand(String command) {
                return null;
            }

            @Override
            public String featureForCallback(String data) {
                return null;
            }

            @Override
            public String featureForState(ConversationStateEntity state) {
                return null;
            }

            @Override
            public String commandForMenuAction(String action) {
                return null;
            }

            @Override
            public void showMenu(BotContext context) {
            }

            @Override
            public void handleCommand(BotContext context, String command, String arguments) {
            }

            @Override
            public boolean handleCallback(BotContext context, String data) {
                return false;
            }

            @Override
            public void handleMessage(BotContext context, String message) {
            }
        };
    }
}
