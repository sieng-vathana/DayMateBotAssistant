package com.daymate.bot.modules.overtime;

import com.daymate.bot.core.module.BotContext;
import com.daymate.bot.core.module.BotModule;
import com.daymate.bot.core.module.FeatureDeclaration;
import com.daymate.bot.core.module.MenuAction;
import com.daymate.bot.domain.ConversationStep;
import com.daymate.bot.entity.ConversationStateEntity;
import com.daymate.bot.i18n.Texts;
import com.daymate.bot.portal.PortalAccountService;
import com.daymate.bot.router.OtHandler;
import com.daymate.bot.telegram.TelegramApiClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class OvertimeModule implements BotModule {
    public static final String ID = "overtime";
    public static final String RECORD = "ot.record";
    public static final String LIST = "ot.list";
    public static final String HISTORY = "ot.history";
    public static final String EDIT = "ot.edit";
    public static final String DELETE = "ot.delete";
    public static final String SALARY = "ot.salary";
    public static final String UPCOMING = "ot.upcoming";
    public static final String REMINDER = "ot.reminder";

    private final OtHandler ot;
    private final PortalAccountService portal;
    private final TelegramApiClient telegram;

    public OvertimeModule(OtHandler ot, PortalAccountService portal, TelegramApiClient telegram) {
        this.ot = ot;
        this.portal = portal;
        this.telegram = telegram;
    }

    @Override
    public String moduleId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Overtime";
    }

    @Override
    public String description() {
        return "Record overtime and see payday.";
    }

    @Override
    public int displayOrder() {
        return 10;
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    @Override
    public List<String> commands() {
        return List.of("/ot", "/ot-record", "/otlist", "/ot-list", "/ot-history", "/othistory", "/ot_history",
                "/del", "/delete", "/salary", "/upcoming_salary", "/upcomingsalary", "/upcoming-salary", "/link");
    }

    @Override
    public List<String> callbackPrefixes() {
        return List.of("ot");
    }

    @Override
    public List<FeatureDeclaration> features() {
        return List.of(
                open(RECORD), open(LIST), open(HISTORY), open(EDIT), open(DELETE),
                open(SALARY), open(UPCOMING), open(REMINDER));
    }

    @Override
    public List<MenuAction> menuActions() {
        return List.of(
                new MenuAction("record", "Record OT", "កត់ OT", RECORD),
                new MenuAction("list", "This month", "ខែនេះ", LIST),
                new MenuAction("history", "History", "ប្រវត្តិ", HISTORY),
                new MenuAction("salary", "Salary", "ប្រាក់ខែ", SALARY),
                new MenuAction("upcoming", "Upcoming payday", "ថ្ងៃបើកប្រាក់", UPCOMING));
    }

    @Override
    public String featureForCommand(String command) {
        return switch (normalize(command)) {
            case "/ot", "/ot-record" -> RECORD;
            case "/otlist", "/ot-list" -> LIST;
            case "/ot-history", "/othistory", "/ot_history" -> HISTORY;
            case "/del", "/delete" -> DELETE;
            case "/salary" -> SALARY;
            case "/upcoming_salary", "/upcomingsalary", "/upcoming-salary" -> UPCOMING;
            case "/link" -> null;
            default -> null;
        };
    }

    @Override
    public String featureForCallback(String data) {
        if (data == null || data.equals("ot:cancel")) {
            return null;
        }
        if (data.startsWith("ot:edittime:") || data.startsWith("ot:editrate:")
                || data.startsWith("ot:editback:") || data.startsWith("ot:edit:")) {
            return EDIT;
        }
        if (data.startsWith("ot:delok:") || data.startsWith("ot:del:")) {
            return DELETE;
        }
        if (data.equals("ot:keep") || data.startsWith("ot:keep:")) {
            return LIST;
        }
        if (data.equals("ot:hist") || data.startsWith("ot:mon:")) {
            return HISTORY;
        }
        if (data.equals("ot:change") || data.startsWith("ot:acleda:")
                || data.startsWith("ot:days:") || data.startsWith("ot:times:")) {
            return SALARY;
        }
        if (data.startsWith("ot:day:") || data.startsWith("ot:go:") || data.equals("ot:home")) {
            return RECORD;
        }
        return null;
    }

    @Override
    public String featureForState(ConversationStateEntity state) {
        if (state == null) {
            return RECORD;
        }
        String pending = state.getPendingRange();
        if (pending != null && pending.startsWith("e:")) {
            return EDIT;
        }
        ConversationStep step = state.getStep();
        if (step == ConversationStep.ACLEDA || step == ConversationStep.WORK_DAYS
                || step == ConversationStep.PAY_TIMES || step == ConversationStep.PAY_DAY
                || step == ConversationStep.PAY_DAY_2 || step == ConversationStep.BASE_SALARY) {
            return SALARY;
        }
        return RECORD;
    }

    @Override
    public String commandForMenuAction(String action) {
        return switch (action) {
            case "record" -> "/ot";
            case "list" -> "/otlist";
            case "history" -> "/ot-history";
            case "salary" -> "/salary";
            case "upcoming" -> "/upcoming_salary";
            default -> null;
        };
    }

    @Override
    public void showMenu(BotContext context) {
        List<List<Map<String, String>>> rows = new ArrayList<>();
        for (MenuAction action : menuActions()) {
            if (context.allows(action.featureKey())) {
                rows.add(List.of(button(action.label(), "menu:" + moduleId() + ":" + action.action())));
            }
        }
        rows.add(List.of(button(Texts.t("Main menu", "ម៉ឺនុយ"), "menu:home")));
        telegram.sendMessage(context.chatId(), Texts.t("Overtime", "ម៉ោងបន្ថែម"), rows);
    }

    @Override
    public void handleCommand(BotContext context, String command, String arguments) {
        Long chatId = context.chatId();
        Long userId = context.userId();
        switch (normalize(command)) {
            case "/ot", "/ot-record" -> ot.startRecord(chatId, userId, arguments);
            case "/otlist", "/ot-list" -> ot.list(chatId, userId);
            case "/ot-history", "/othistory", "/ot_history" -> ot.history(chatId, userId, arguments);
            case "/del", "/delete" -> ot.deleteCommand(chatId, userId, arguments);
            case "/salary" -> ot.startSalary(chatId, userId);
            case "/upcoming_salary", "/upcomingsalary", "/upcoming-salary" -> ot.upcoming(chatId, userId);
            case "/link" -> {
                String code = portal.issueLinkCode(userId);
                telegram.sendMessage(chatId, Texts.t(
                        "Open the portal: " + portal.portalUrl() + "\nLog in, then enter this code:\n" + code
                                + "\nIt expires in 15 minutes.",
                        "បើកវេប៖ " + portal.portalUrl() + "\nចូលគណនី បន្ទាប់មកបញ្ចូលលេខកូដនេះ៖\n" + code
                                + "\nផុតកំណត់ក្នុង 15 នាទី។"));
            }
            default -> telegram.sendMessage(chatId, Texts.t(
                    "I don't know that command. Send /help to see what I can do.",
                    "ខ្ញុំមិនស្គាល់ពាក្យបញ្ជានេះទេ។ ផ្ញើ /help ដើម្បីមើលអ្វីដែលខ្ញុំធ្វើបាន។"));
        }
    }

    @Override
    public boolean handleCallback(BotContext context, String data) {
        return ot.handleCallback(context.callbackId(), context.chatId(), context.userId(), data);
    }

    @Override
    public void handleMessage(BotContext context, String message) {
        ot.handleText(context.state(), context.chatId(), message);
    }

    private static FeatureDeclaration open(String key) {
        return new FeatureDeclaration(key, true);
    }

    private static String normalize(String command) {
        return command == null ? "" : command.toLowerCase(Locale.ROOT);
    }

    private static Map<String, String> button(String text, String data) {
        return Map.of("text", text, "callback_data", data);
    }
}
