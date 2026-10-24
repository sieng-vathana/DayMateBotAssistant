package com.daymate.bot.core.module;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class ModuleRegistry {
    private final List<BotModule> modules;
    private final Map<String, BotModule> byId;

    public ModuleRegistry(List<BotModule> modules) {
        Map<String, BotModule> found = new LinkedHashMap<>();
        for (BotModule module : modules) {
            BotModule previous = found.putIfAbsent(module.moduleId(), module);
            if (previous != null) {
                throw new IllegalStateException("Duplicate module id: " + module.moduleId());
            }
        }
        this.byId = Map.copyOf(found);
        this.modules = List.copyOf(found.values());
    }

    public List<BotModule> all() {
        return modules;
    }

    public Optional<BotModule> find(String moduleId) {
        if (moduleId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(moduleId));
    }

    public Optional<BotModule> findByCommand(String command) {
        if (command == null) {
            return Optional.empty();
        }
        String normalized = command.toLowerCase(Locale.ROOT);
        return modules.stream()
                .filter(module -> module.commands().stream().anyMatch(candidate -> candidate.equalsIgnoreCase(normalized)))
                .findFirst();
    }

    public Optional<BotModule> findByCallback(String data) {
        if (data == null || data.isBlank()) {
            return Optional.empty();
        }
        BotModule match = null;
        int best = -1;
        for (BotModule module : modules) {
            for (String prefix : module.callbackPrefixes()) {
                if (prefix != null && (data.equals(prefix) || data.startsWith(prefix + ":")) && prefix.length() > best) {
                    match = module;
                    best = prefix.length();
                }
            }
        }
        return Optional.ofNullable(match);
    }
}
