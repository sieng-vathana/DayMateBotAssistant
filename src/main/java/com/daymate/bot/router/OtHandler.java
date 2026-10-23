package com.daymate.bot.router;

import com.daymate.bot.domain.ConversationStep;
import com.daymate.bot.domain.OtTimeRange;
import com.daymate.bot.entity.ConversationStateEntity;
import com.daymate.bot.entity.SalaryProfileEntity;
import com.daymate.bot.ot.OtFormatter;
import com.daymate.bot.ot.OtRecordService;
import com.daymate.bot.ot.OtTimeParser;
import com.daymate.bot.service.ConversationService;
import com.daymate.bot.telegram.TelegramApiClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class OtHandler {
    private final ConversationService conversations;
    private final OtRecordService ot;
    private final OtFormatter formatter;
    private final TelegramApiClient telegram;
    private final Clock clock;

    public OtHandler(ConversationService conversations, OtRecordService ot, OtFormatter formatter,
                     TelegramApiClient telegram, Clock clock) {
        this.conversations = conversations;
        this.ot = ot;
        this.formatter = formatter;
        this.telegram = telegram;
        this.clock = clock;
    }

    public void startRecord(Long chatId, Long userId, String argument) {
        Optional<OtTimeRange> preset = argument == null || argument.isBlank()
                ? Optional.empty()
                : OtTimeParser.parse(argument);
        if (argument != null && !argument.isBlank() && preset.isEmpty()) {
            askTime(chatId, userId);
            telegram.sendHtml(chatId, formatter.invalidTime(), cancelKeyboard());
            return;
        }
        if (ot.salary(userId).isEmpty()) {
            ConversationStateEntity state = conversations.start(chatId, userId);
            state.setStep(ConversationStep.ACLEDA);
            state.setResumeOt(true);
            preset.ifPresent(range -> state.setPendingRange(range.start() + "-" + range.end()));
            conversations.save(state);
            telegram.sendMessage(chatId, formatter.acledaPrompt(), acledaKeyboard());
            return;
        }
        if (preset.isPresent()) {
            saveRange(chatId, userId, preset.get());
            return;
        }
        askTime(chatId, userId);
    }

    public void startSalary(Long chatId, Long userId) {
        ot.salary(userId).ifPresentOrElse(
                profile -> telegram.sendMessage(chatId, formatter.salarySummary(profile), changeKeyboard()),
                () -> beginSalary(chatId, userId, false));
    }

    public void list(Long chatId, Long userId) {
        telegram.sendMessage(chatId, formatter.list(ot.monthSummary(userId, ot.currentMonth())));
    }

    public void handleText(ConversationStateEntity state, Long chatId, String text) {
        switch (state.getStep()) {
            case ACLEDA -> acceptAcleda(state, chatId, text);
            case PAY_TIMES -> acceptTimes(state, chatId, text);
            case PAY_DAY, PAY_DAY_2 -> acceptDay(state, chatId, text);
            case BASE_SALARY -> acceptSalary(state, chatId, text);
            case OT_TIME -> acceptTime(state, chatId, text);
            default -> telegram.sendMessage(chatId, formatter.needSalaryFirst());
        }
    }

    public boolean handleCallback(String callbackId, Long chatId, Long userId, String data) {
        if (data.equals("ot:cancel")) {
            conversations.clear(chatId);
            telegram.answerCallback(callbackId, "Cancelled", false);
            telegram.sendMessage(chatId, formatter.cancelled());
            return true;
        }
        if (data.equals("ot:change")) {
            telegram.answerCallback(callbackId, "Update salary", false);
            beginSalary(chatId, userId, false);
            return true;
        }
        ConversationStateEntity state = conversations.get(chatId).orElse(null);
        if (state == null || !state.getStep().isOtSetup()) {
            telegram.answerCallback(callbackId, "That step expired. Send /ot to start again.", true);
            return true;
        }
        telegram.answerCallback(callbackId, "Saved", false);
        if (data.equals("ot:acleda:yes")) {
            state.setAcledaEmployee(true);
            state.setPayTimes(2);
            state.setPayDay1(12);
            state.setStep(ConversationStep.BASE_SALARY);
            conversations.save(state);
            telegram.sendMessage(chatId, formatter.salaryAmountPrompt(true, 2, 12, 26));
            return true;
        }
        if (data.equals("ot:acleda:no")) {
            state.setAcledaEmployee(false);
            state.setStep(ConversationStep.PAY_TIMES);
            conversations.save(state);
            telegram.sendMessage(chatId, formatter.payTimesPrompt(), timesKeyboard());
            return true;
        }
        if (data.equals("ot:times:1") || data.equals("ot:times:2")) {
            acceptTimes(state, chatId, data.endsWith("2") ? "2" : "1");
            return true;
        }
        return false;
    }

    private void beginSalary(Long chatId, Long userId, boolean resumeOt) {
        ConversationStateEntity state = conversations.start(chatId, userId);
        state.setStep(ConversationStep.ACLEDA);
        state.setResumeOt(resumeOt);
        conversations.save(state);
        telegram.sendMessage(chatId, formatter.acledaPrompt(), acledaKeyboard());
    }

    private void acceptAcleda(ConversationStateEntity state, Long chatId, String text) {
        String value = text.trim().toLowerCase();
        if (value.equals("yes") || value.equals("y") || value.equals("acleda")) {
            state.setAcledaEmployee(true);
            state.setPayTimes(2);
            state.setPayDay1(12);
            state.setStep(ConversationStep.BASE_SALARY);
            conversations.save(state);
            telegram.sendMessage(chatId, formatter.salaryAmountPrompt(true, 2, 12, 26));
            return;
        }
        if (value.equals("no") || value.equals("n")) {
            state.setAcledaEmployee(false);
            state.setStep(ConversationStep.PAY_TIMES);
            conversations.save(state);
            telegram.sendMessage(chatId, formatter.payTimesPrompt(), timesKeyboard());
            return;
        }
        telegram.sendMessage(chatId, "Choose Yes or No.", acledaKeyboard());
    }

    private void acceptTimes(ConversationStateEntity state, Long chatId, String text) {
        String value = text.trim().toLowerCase();
        int times;
        if (value.equals("1") || value.startsWith("1 ")) {
            times = 1;
        } else if (value.equals("2") || value.startsWith("2 ")) {
            times = 2;
        } else {
            telegram.sendMessage(chatId, formatter.payTimesPrompt(), timesKeyboard());
            return;
        }
        state.setPayTimes(times);
        state.setStep(ConversationStep.PAY_DAY);
        conversations.save(state);
        telegram.sendMessage(chatId, formatter.payDayPrompt(1, times));
    }

    private void acceptDay(ConversationStateEntity state, Long chatId, String text) {
        Integer day = parseDay(text);
        if (day == null) {
            telegram.sendMessage(chatId, formatter.invalidDay());
            return;
        }
        if (state.getStep() == ConversationStep.PAY_DAY) {
            state.setPayDay1(day);
            if (state.getPayTimes() != null && state.getPayTimes() == 2) {
                state.setStep(ConversationStep.PAY_DAY_2);
                conversations.save(state);
                telegram.sendMessage(chatId, formatter.payDayPrompt(2, 2));
                return;
            }
            state.setStep(ConversationStep.BASE_SALARY);
            conversations.save(state);
            telegram.sendMessage(chatId, formatter.salaryAmountPrompt(false, 1, day, null));
            return;
        }
        if (state.getPayDay1() != null && state.getPayDay1().equals(day)) {
            telegram.sendMessage(chatId, "The two paydays must be different days.");
            return;
        }
        state.setPayDay2(day);
        state.setPayTimes(2);
        state.setStep(ConversationStep.BASE_SALARY);
        conversations.save(state);
        telegram.sendMessage(chatId, formatter.salaryAmountPrompt(false, 2, state.getPayDay1(), day));
    }

    private void acceptSalary(ConversationStateEntity state, Long chatId, String text) {
        BigDecimal amount = parseMoney(text);
        if (amount == null) {
            telegram.sendMessage(chatId, formatter.invalidSalary());
            return;
        }
        boolean acleda = Boolean.TRUE.equals(state.getAcledaEmployee());
        int times = acleda ? 2 : state.getPayTimes();
        int day1 = acleda ? 12 : state.getPayDay1();
        Integer day2 = null;
        if (times == 2) {
            day2 = acleda ? 26 : state.getPayDay2();
            if (day2 == null) {
                telegram.sendMessage(chatId, formatter.invalidDay());
                return;
            }
        }
        SalaryProfileEntity profile = ot.saveSalary(state.getUserId(), acleda, times, day1, day2, amount);
        String pending = state.getPendingRange();
        boolean resume = state.isResumeOt();
        conversations.clear(chatId);
        telegram.sendMessage(chatId, formatter.salarySaved(profile));
        if (pending != null) {
            OtTimeParser.parse(pending).ifPresent(range -> saveRange(chatId, state.getUserId(), range));
            return;
        }
        if (resume) {
            askTime(chatId, state.getUserId());
        }
    }

    private void acceptTime(ConversationStateEntity state, Long chatId, String text) {
        Optional<OtTimeRange> range = OtTimeParser.parse(text);
        if (range.isEmpty()) {
            telegram.sendHtml(chatId, formatter.invalidTime(), cancelKeyboard());
            return;
        }
        conversations.clear(chatId);
        saveRange(chatId, state.getUserId(), range.get());
    }

    private void askTime(Long chatId, Long userId) {
        ConversationStateEntity state = new ConversationStateEntity(chatId, userId, ConversationStep.OT_TIME, Instant.now(clock));
        conversations.save(state);
        telegram.sendMessage(chatId, formatter.timePrompt(), cancelKeyboard());
    }

    private void saveRange(Long chatId, Long userId, OtTimeRange range) {
        try {
            telegram.sendMessage(chatId, formatter.saved(ot.record(userId, chatId, range)));
        } catch (IllegalArgumentException exception) {
            telegram.sendMessage(chatId, exception.getMessage(), cancelKeyboard());
        }
    }

    private static Integer parseDay(String text) {
        try {
            int day = Integer.parseInt(text.trim());
            return day >= 1 && day <= 31 ? day : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static BigDecimal parseMoney(String text) {
        String cleaned = text.trim().replace("$", "").replace(",", "");
        try {
            BigDecimal value = new BigDecimal(cleaned);
            return value.signum() > 0 ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static List<List<Map<String, String>>> acledaKeyboard() {
        return List.of(List.of(button("Yes, ACLEDA", "ot:acleda:yes"), button("No", "ot:acleda:no")));
    }

    private static List<List<Map<String, String>>> timesKeyboard() {
        return List.of(List.of(button("1 time", "ot:times:1"), button("2 times", "ot:times:2")));
    }

    private static List<List<Map<String, String>>> cancelKeyboard() {
        return List.of(List.of(button("❌ Cancel", "ot:cancel")));
    }

    private static List<List<Map<String, String>>> changeKeyboard() {
        return List.of(List.of(button("Change salary", "ot:change")));
    }

    private static Map<String, String> button(String text, String data) {
        return Map.of("text", text, "callback_data", data);
    }
}
