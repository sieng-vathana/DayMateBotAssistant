package com.daymate.bot.modules.developer;

import com.daymate.bot.modules.prepared.PreparedModule;
import com.daymate.bot.telegram.TelegramApiClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DeveloperModule extends PreparedModule {
    public DeveloperModule(TelegramApiClient telegram) {
        super("developer", "Developer Tools", "Developer utilities.", 50,
                List.of("/dev", "/developer"), "developer.open", telegram);
    }
}
