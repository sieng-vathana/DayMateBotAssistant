package com.daymate.bot.router;

import com.daymate.bot.domain.ConversationStep;
import com.daymate.bot.domain.OtShift;
import com.daymate.bot.domain.OtTimeRange;
import com.daymate.bot.entity.ConversationStateEntity;
import com.daymate.bot.entity.SalaryProfileEntity;
import com.daymate.bot.ot.OtEntryParser;
import com.daymate.bot.ot.OtFormatter;
import com.daymate.bot.ot.OtRecordService;
import com.daymate.bot.service.ConversationService;
import com.daymate.bot.telegram.TelegramApiClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
        if (ot.salary(userId).isEmpty()) {
            ConversationStateEntity state = conversations.start(chatId, userId);
            state.setStep(ConversationStep.ACLEDA);
            state.setResumeOt(true);
            if (argument != null && !argument.isBlank()) {
                state.setPendingRange(argument.trim());
            }
            conversations.save(state);
            telegram.sendMessage(chatId, formatter.acledaPrompt(), acledaKeyboard());
            return;
        }
        if (argument != null && !argument.isBlank()) {
            Optional<OtEntryParser.ParsedOt> parsed = OtEntryParser.parse(argument, ot.today());
            if (parsed.isPresent()) {
                saveParsed(chatId, userId, parsed.get());
                return;
            }
            Optional<LocalDate> day = OtEntryParser.parseDay(argument, ot.today());
            if (day.isPresent()) {
                showDay(chatId, userId, day.get());
                return;
            }
            showHome(chatId, userId);
            telegram.sendHtml(chatId, formatter.invalidEntry(), null);
            return;
        }
        showHome(chatId, userId);
    }

    public void startSalary(Long chatId, Long userId) {
        ot.salary(userId).ifPresentOrElse(
                profile -> telegram.sendMessage(chatId, formatter.salarySummary(profile), changeKeyboard()),
                () -> beginSalary(chatId, userId, false));
    }

    public void list(Long chatId, Long userId) {
        telegram.sendMessage(chatId, formatter.list(ot.monthSummary(userId, ot.currentMonth())));
    }

    public void upcoming(Long chatId, Long userId) {
        try {
            telegram.sendMessage(chatId, formatter.upcoming(ot.upcoming(userId)));
        } catch (IllegalArgumentException exception) {
            telegram.sendMessage(chatId, exception.getMessage());
        }
    }

    public void handleText(ConversationStateEntity state, Long chatId, String text) {
        switch (state.getStep()) {
            case ACLEDA -> acceptAcleda(state, chatId, text);
            case WORK_DAYS -> acceptWorkDays(state, chatId, text);
            case PAY_TIMES -> acceptTimes(state, chatId, text);
            case PAY_DAY, PAY_DAY_2 -> acceptDay(state, chatId, text);
            case BASE_SALARY -> acceptSalary(state, chatId, text);
            case OT_TIME, OT_TYPE -> acceptLine(state, chatId, text);
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
        if (data.equals("ot:home")) {
            telegram.answerCallback(callbackId, "Days", false);
            showHome(chatId, userId);
            return true;
        }
        if (data.startsWith("ot:day:")) {
            telegram.answerCallback(callbackId, "Day", false);
            showDay(chatId, userId, LocalDate.parse(data.substring("ot:day:".length())));
            return true;
        }
        if (data.startsWith("ot:go:")) {
            telegram.answerCallback(callbackId, "Saved", false);
            if (ot.salary(userId).isEmpty()) {
                telegram.sendMessage(chatId, formatter.needSalaryFirst());
                return true;
            }
            saveFromToken(chatId, userId, data);
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
            state.setWorkDaysPerWeek(5);
            state.setPayTimes(2);
            state.setPayDay1(12);
            state.setStep(ConversationStep.BASE_SALARY);
            conversations.save(state);
            telegram.sendMessage(chatId, formatter.salaryAmountPrompt(true, 2, 12, 26));
            return true;
        }
        if (data.equals("ot:acleda:no")) {
            state.setAcledaEmployee(false);
            state.setStep(ConversationStep.WORK_DAYS);
            conversations.save(state);
            telegram.sendMessage(chatId, formatter.workDaysPrompt(), workDaysKeyboard());
            return true;
        }
        if (data.equals("ot:days:5") || data.equals("ot:days:6")) {
            acceptWorkDays(state, chatId, data.endsWith("6") ? "6" : "5");
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
            state.setWorkDaysPerWeek(5);
            state.setPayTimes(2);
            state.setPayDay1(12);
            state.setStep(ConversationStep.BASE_SALARY);
            conversations.save(state);
            telegram.sendMessage(chatId, formatter.salaryAmountPrompt(true, 2, 12, 26));
            return;
        }
        if (value.equals("no") || value.equals("n")) {
            state.setAcledaEmployee(false);
            state.setStep(ConversationStep.WORK_DAYS);
            conversations.save(state);
            telegram.sendMessage(chatId, formatter.workDaysPrompt(), workDaysKeyboard());
            return;
        }
        telegram.sendMessage(chatId, "Choose Yes or No.", acledaKeyboard());
    }

    private void acceptWorkDays(ConversationStateEntity state, Long chatId, String text) {
        String value = text.trim().toLowerCase();
        int days;
        if (value.startsWith("5")) {
            days = 5;
        } else if (value.startsWith("6")) {
            days = 6;
        } else {
            telegram.sendMessage(chatId, formatter.workDaysPrompt(), workDaysKeyboard());
            return;
        }
        state.setWorkDaysPerWeek(days);
        state.setStep(ConversationStep.PAY_TIMES);
        conversations.save(state);
        telegram.sendMessage(chatId, formatter.payTimesPrompt(), timesKeyboard());
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
        int workDays = acleda ? 5 : (state.getWorkDaysPerWeek() == null ? 5 : state.getWorkDaysPerWeek());
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
        SalaryProfileEntity profile = ot.saveSalary(state.getUserId(), acleda, times, day1, day2, workDays, amount);
        String pending = state.getPendingRange();
        boolean resume = state.isResumeOt();
        conversations.clear(chatId);
        telegram.sendMessage(chatId, formatter.salarySaved(profile));
        if (pending != null && !pending.isBlank()) {
            Optional<OtEntryParser.ParsedOt> parsed = OtEntryParser.parse(pending, ot.today());
            if (parsed.isPresent()) {
                saveParsed(chatId, state.getUserId(), parsed.get());
                return;
            }
        }
        if (resume) {
            showHome(chatId, state.getUserId());
        }
    }

    private void acceptLine(ConversationStateEntity state, Long chatId, String text) {
        Optional<OtEntryParser.ParsedOt> parsed = OtEntryParser.parse(text, ot.today());
        if (parsed.isPresent()) {
            saveParsed(chatId, state.getUserId(), parsed.get());
            return;
        }
        Optional<LocalDate> day = OtEntryParser.parseDay(text, ot.today());
        if (day.isPresent()) {
            showDay(chatId, state.getUserId(), day.get());
            return;
        }
        telegram.sendHtml(chatId, formatter.invalidEntry(), null);
    }

    private void showHome(Long chatId, Long userId) {
        ConversationStateEntity state = new ConversationStateEntity(
                chatId, userId, ConversationStep.OT_TIME, Instant.now(clock));
        conversations.save(state);
        LocalDate today = ot.today();
        telegram.sendHtml(chatId, formatter.oneLinePrompt(today), homeKeyboard(today, workDays(userId)));
    }

    private void showDay(Long chatId, Long userId, LocalDate date) {
        ConversationStateEntity state = new ConversationStateEntity(
                chatId, userId, ConversationStep.OT_TIME, Instant.now(clock));
        state.setPendingRange(date.toString());
        conversations.save(state);
        telegram.sendMessage(chatId, formatter.dayChosen(date), timeKeyboard(date, workDays(userId)));
    }

    private void saveParsed(Long chatId, Long userId, OtEntryParser.ParsedOt parsed) {
        int week = workDays(userId);
        OtShift shift = parsed.shift() == null ? OtShift.forDate(parsed.date(), week) : parsed.shift();
        conversations.clear(chatId);
        saveRange(chatId, userId, parsed.date(), parsed.range(), shift);
    }

    private void saveFromToken(Long chatId, Long userId, String data) {
        String[] parts = data.split(":");
        if (parts.length != 6) {
            telegram.sendHtml(chatId, formatter.invalidEntry(), null);
            return;
        }
        LocalDate date = LocalDate.parse(parts[2], DateTimeFormatter.BASIC_ISO_DATE);
        LocalTime start = LocalTime.parse(parts[3], DateTimeFormatter.ofPattern("HHmm"));
        LocalTime end = LocalTime.parse(parts[4], DateTimeFormatter.ofPattern("HHmm"));
        OtShift shift = switch (parts[5]) {
            case "H" -> OtShift.HOLIDAY;
            case "W" -> OtShift.WEEKEND;
            default -> OtShift.EVENING;
        };
        conversations.clear(chatId);
        saveRange(chatId, userId, date, new OtTimeRange(start, end), shift);
    }

    private void saveRange(Long chatId, Long userId, LocalDate date, OtTimeRange range, OtShift shift) {
        try {
            telegram.sendMessage(chatId, formatter.saved(ot.record(userId, chatId, date, range, shift)));
        } catch (IllegalArgumentException exception) {
            telegram.sendMessage(chatId, exception.getMessage(), cancelKeyboard());
        }
    }

    private int workDays(Long userId) {
        return ot.salary(userId).map(SalaryProfileEntity::resolvedWorkDaysPerWeek).orElse(5);
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

    private List<List<Map<String, String>>> homeKeyboard(LocalDate today, int workDays) {
        List<List<Map<String, String>>> rows = new ArrayList<>();
        rows.add(List.of(quick(today, "16:30", "17:30", workDays, "Today 16:30-17:30")));
        rows.add(List.of(quick(today, "16:30", "18:30", workDays, "Today 16:30-18:30")));
        YearMonth month = YearMonth.from(today);
        List<Map<String, String>> row = new ArrayList<>();
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate date = month.atDay(day);
            String label = date.equals(today) ? "•" + day : Integer.toString(day);
            row.add(button(label, "ot:day:" + date));
            if (row.size() == 7) {
                rows.add(row);
                row = new ArrayList<>();
            }
        }
        if (!row.isEmpty()) {
            rows.add(row);
        }
        rows.add(List.of(button("❌ Cancel", "ot:cancel")));
        return rows;
    }

    private List<List<Map<String, String>>> timeKeyboard(LocalDate date, int workDays) {
        OtShift auto = OtShift.forDate(date, workDays);
        return List.of(
                List.of(slot(date, "16:30", "17:30", auto)),
                List.of(slot(date, "16:30", "18:30", auto)),
                List.of(slot(date, "09:00", "17:30", auto)),
                List.of(slot(date, "08:30", "17:30", OtShift.HOLIDAY)),
                List.of(button("Another day", "ot:home"), button("❌ Cancel", "ot:cancel")));
    }

    private Map<String, String> quick(LocalDate date, String start, String end, int workDays, String label) {
        return button(label, token(date, start, end, OtShift.forDate(date, workDays)));
    }

    private Map<String, String> slot(LocalDate date, String start, String end, OtShift shift) {
        return button(start + "-" + end + " · " + shift.label(), token(date, start, end, shift));
    }

    private static String token(LocalDate date, String start, String end, OtShift shift) {
        String code = switch (shift) {
            case HOLIDAY -> "H";
            case WEEKEND -> "W";
            case EVENING -> "E";
        };
        return "ot:go:" + date.format(DateTimeFormatter.BASIC_ISO_DATE) + ":"
                + start.replace(":", "") + ":" + end.replace(":", "") + ":" + code;
    }

    private static List<List<Map<String, String>>> workDaysKeyboard() {
        return List.of(List.of(button("5 days", "ot:days:5"), button("6 days", "ot:days:6")));
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
