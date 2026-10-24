package com.daymate.bot.modules.finance;

import com.daymate.bot.modules.prepared.PreparedModule;
import com.daymate.bot.telegram.TelegramApiClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FinanceModule extends PreparedModule {
    public FinanceModule(TelegramApiClient telegram) {
        super("finance", "Finance", "Income, expenses, and budgets.", 30,
                List.of("/finance"), "finance.open", telegram);
    }
}
