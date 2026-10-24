package com.daymate.bot.modules.save;

import com.daymate.bot.modules.prepared.PreparedModule;
import com.daymate.bot.telegram.TelegramApiClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SaveModule extends PreparedModule {
    public SaveModule(TelegramApiClient telegram) {
        super("save", "Save", "Saved notes.", 40, List.of("/save"), "save.open", telegram);
    }
}
