package com.daymate.bot.router;

import com.daymate.bot.domain.ConversationStep;
import com.daymate.bot.domain.OtShift;
import com.daymate.bot.domain.OtTimeRange;
import com.daymate.bot.i18n.Texts;
import com.daymate.bot.entity.ConversationStateEntity;
import com.daymate.bot.entity.OtRecordEntity;
import com.daymate.bot.entity.SalaryProfileEntity;
import com.daymate.bot.ot.OtEntryParser;
import com.daymate.bot.ot.OtFormatter;
import com.daymate.bot.ot.MonthParser;
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
    private static final String[][] SUGGESTED_RANGES = {
            {"07:30", "16:30"},
            {"08:30", "17:30"},
            {"09:30", "18:30"}
    };

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
        showMonth(chatId, userId, ot.currentMonth());
    }

    public void history(Long chatId, Long userId, String argument) {
        if (argument == null || argument.isBlank()) {
            showHistory(chatId, userId);
            return;
        }
        Optional<YearMonth> month = MonthParser.parse(argument, ot.today());
        if (month.isEmpty()) {
            telegram.sendHtml(chatId, formatter.invalidMonth(), null);
            return;
        }
        showMonth(chatId, userId, month.get());
    }

    public void deleteCommand(Long chatId, Long userId, String argument) {
        if (argument == null || argument.isBlank()) {
            telegram.sendMessage(chatId, formatter.deleteUsage());
            return;
        }
        String[] parts = argument.trim().split("\\s+");
        String numberText = parts[0].startsWith("#") ? parts[0].substring(1) : parts[0];
        int number;
        try {
            number = Integer.parseInt(numberText);
        } catch (NumberFormatException exception) {
            telegram.sendMessage(chatId, formatter.deleteUsage());
            return;
        }
        YearMonth month = ot.currentMonth();
        if (parts.length > 1) {
            Optional<YearMonth> parsed = MonthParser.parse(parts[1], ot.today());
            if (parsed.isEmpty()) {
                telegram.sendHtml(chatId, formatter.invalidMonth(), null);
                return;
            }
            month = parsed.get();
        }
        Optional<com.daymate.bot.entity.OtRecordEntity> record = ot.findRecord(userId, number, month);
        if (record.isEmpty()) {
            telegram.sendMessage(chatId, formatter.recordNotFound(number));
            return;
        }
        askDelete(chatId, record.get());
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
        if (data.equals("ot:hist")) {
            telegram.answerCallback(callbackId, "History", false);
            showHistory(chatId, userId);
            return true;
        }
        if (data.startsWith("ot:mon:")) {
            telegram.answerCallback(callbackId, "Month", false);
            showMonth(chatId, userId, YearMonth.parse(data.substring("ot:mon:".length())));
            return true;
        }
        if (data.startsWith("ot:delok:")) {
            telegram.answerCallback(callbackId, "Deleted", false);
            deleteFromToken(chatId, userId, data.substring("ot:delok:".length()));
            return true;
        }
        if (data.startsWith("ot:keep:")) {
            telegram.answerCallback(callbackId, "Kept", false);
            showMonth(chatId, userId, YearMonth.parse(data.substring("ot:keep:".length())));
            return true;
        }
        if (data.startsWith("ot:del:")) {
            telegram.answerCallback(callbackId, "Delete", false);
            confirmFromToken(chatId, userId, data.substring("ot:del:".length()));
            return true;
        }
        if (data.startsWith("ot:edittime:")) {
            telegram.answerCallback(callbackId, "Edit", false);
            editTimeFromToken(chatId, userId, data);
            return true;
        }
        if (data.startsWith("ot:editrate:")) {
            telegram.answerCallback(callbackId, "Edit", false);
            editRateFromToken(chatId, userId, data);
            return true;
        }
        if (data.startsWith("ot:editback:")) {
            telegram.answerCallback(callbackId, "List", false);
            conversations.clear(chatId);
            try {
                showMonth(chatId, userId, YearMonth.parse(data.substring("ot:editback:".length())));
            } catch (java.time.DateTimeException exception) {
                telegram.sendHtml(chatId, formatter.invalidMonth(), null);
            }
            return true;
        }
        if (data.startsWith("ot:edit:")) {
            telegram.answerCallback(callbackId, "Edit", false);
            openEdit(chatId, userId, data.substring("ot:edit:".length()));
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
        telegram.sendMessage(chatId, formatter.chooseYesNo(), acledaKeyboard());
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
            telegram.sendMessage(chatId, formatter.differentPaydays());
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
        Optional<EditTarget> editing = editTarget(state);
        if (editing.isPresent()) {
            acceptEditLine(state, chatId, text, editing.get());
            return;
        }
        Optional<OtEntryParser.ParsedOt> parsed = OtEntryParser.parse(text, ot.today());
        if (parsed.isPresent()) {
            saveParsed(chatId, state.getUserId(), parsed.get());
            return;
        }
        LocalDate chosen = chosenDate(state);
        if (chosen != null) {
            Optional<OtEntryParser.TimeOnly> typed = OtEntryParser.parseTimeOnly(text);
            if (typed.isPresent()) {
                OtShift shift = typed.get().shift() == null
                        ? OtShift.forDate(chosen, workDays(state.getUserId()))
                        : typed.get().shift();
                conversations.clear(chatId);
                saveRange(chatId, state.getUserId(), chosen, typed.get().range(), shift);
                return;
            }
        }
        Optional<LocalDate> day = OtEntryParser.parseDay(text, ot.today());
        if (day.isPresent()) {
            showDay(chatId, state.getUserId(), day.get());
            return;
        }
        boolean weekend = chosen != null && OtShift.forDate(chosen, workDays(state.getUserId())) == OtShift.WEEKEND;
        telegram.sendHtml(chatId, chosen == null ? formatter.invalidEntry() : formatter.invalidTimeForDay(weekend), null);
    }

    private void acceptEditLine(ConversationStateEntity state, Long chatId, String text, EditTarget target) {
        Optional<OtRecordEntity> record = ot.findRecord(state.getUserId(), target.number(), target.month());
        if (record.isEmpty()) {
            conversations.clear(chatId);
            telegram.sendMessage(chatId, formatter.recordNotFound(target.number()));
            return;
        }
        Optional<OtEntryParser.TimeOnly> typed = OtEntryParser.parseTimeOnly(text);
        if (typed.isEmpty()) {
            Optional<OtEntryParser.ParsedOt> parsed = OtEntryParser.parse(text, ot.today());
            if (parsed.isPresent()) {
                typed = Optional.of(new OtEntryParser.TimeOnly(parsed.get().range(), parsed.get().shift()));
            }
        }
        if (typed.isEmpty()) {
            boolean weekend = OtShift.forDate(record.get().getWorkDate(), workDays(state.getUserId())) == OtShift.WEEKEND;
            telegram.sendHtml(chatId, formatter.invalidTimeForDay(weekend), null);
            return;
        }
        OtShift shift = typed.get().shift() == null ? record.get().getShift() : typed.get().shift();
        applyEdit(chatId, state.getUserId(), target.month(), target.number(), typed.get().range(), shift);
    }

    private static Optional<EditTarget> editTarget(ConversationStateEntity state) {
        String pending = state.getPendingRange();
        if (pending == null || !pending.startsWith("e:")) {
            return Optional.empty();
        }
        String[] parts = pending.split(":");
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            return Optional.of(new EditTarget(YearMonth.parse(parts[1]), Integer.parseInt(parts[2])));
        } catch (IllegalArgumentException | java.time.DateTimeException exception) {
            return Optional.empty();
        }
    }

    private static LocalDate chosenDate(ConversationStateEntity state) {
        String pending = state.getPendingRange();
        if (pending == null || !pending.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return null;
        }
        return LocalDate.parse(pending);
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
        int days = workDays(userId);
        boolean weekend = OtShift.forDate(date, days) == OtShift.WEEKEND;
        telegram.sendHtml(chatId, formatter.dayChosen(date, weekend), timeKeyboard(date, days));
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

    private void openEdit(Long chatId, Long userId, String token) {
        String[] parts = token.split(":");
        if (parts.length != 2) {
            telegram.sendHtml(chatId, formatter.invalidEntry(), null);
            return;
        }
        try {
            showEdit(chatId, userId, YearMonth.parse(parts[0]), Integer.parseInt(parts[1]));
        } catch (IllegalArgumentException | java.time.DateTimeException exception) {
            telegram.sendHtml(chatId, formatter.invalidEntry(), null);
        }
    }

    private void showEdit(Long chatId, Long userId, YearMonth month, int number) {
        Optional<OtRecordEntity> record = ot.findRecord(userId, number, month);
        if (record.isEmpty()) {
            telegram.sendMessage(chatId, formatter.recordNotFound(number));
            return;
        }
        ConversationStateEntity state = new ConversationStateEntity(
                chatId, userId, ConversationStep.OT_TIME, Instant.now(clock));
        state.setPendingRange("e:" + month + ":" + number);
        conversations.save(state);
        int days = workDays(userId);
        boolean weekend = OtShift.forDate(record.get().getWorkDate(), days) == OtShift.WEEKEND;
        telegram.sendHtml(chatId, formatter.editPrompt(record.get(), weekend), editKeyboard(record.get(), days));
    }

    private void editTimeFromToken(Long chatId, Long userId, String data) {
        String[] parts = data.split(":");
        if (parts.length != 7) {
            telegram.sendHtml(chatId, formatter.invalidEntry(), null);
            return;
        }
        try {
            YearMonth month = YearMonth.parse(parts[2], DateTimeFormatter.ofPattern("yyyyMM"));
            int number = Integer.parseInt(parts[3]);
            LocalTime start = LocalTime.parse(parts[4], DateTimeFormatter.ofPattern("HHmm"));
            LocalTime end = LocalTime.parse(parts[5], DateTimeFormatter.ofPattern("HHmm"));
            applyEdit(chatId, userId, month, number, new OtTimeRange(start, end), shiftFromCode(parts[6]));
        } catch (IllegalArgumentException | java.time.DateTimeException exception) {
            telegram.sendHtml(chatId, formatter.invalidEntry(), null);
        }
    }

    private void editRateFromToken(Long chatId, Long userId, String data) {
        String[] parts = data.split(":");
        if (parts.length != 5) {
            telegram.sendHtml(chatId, formatter.invalidEntry(), null);
            return;
        }
        try {
            YearMonth month = YearMonth.parse(parts[2]);
            int number = Integer.parseInt(parts[3]);
            Optional<OtRecordEntity> record = ot.findRecord(userId, number, month);
            if (record.isEmpty()) {
                telegram.sendMessage(chatId, formatter.recordNotFound(number));
                return;
            }
            applyEdit(chatId, userId, month, number,
                    new OtTimeRange(record.get().getStartTime(), record.get().getEndTime()),
                    shiftFromCode(parts[4]));
        } catch (IllegalArgumentException | java.time.DateTimeException exception) {
            telegram.sendHtml(chatId, formatter.invalidEntry(), null);
        }
    }

    private void applyEdit(Long chatId, Long userId, YearMonth month, int number, OtTimeRange range, OtShift shift) {
        try {
            telegram.sendMessage(chatId, formatter.updated(ot.update(userId, number, month, range, shift)));
            conversations.clear(chatId);
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("not found")) {
                conversations.clear(chatId);
            }
            telegram.sendMessage(chatId, exception.getMessage());
        }
    }

    private void showMonth(Long chatId, Long userId, YearMonth month) {
        OtRecordService.MonthSummary summary = ot.monthSummary(userId, month);
        telegram.sendHtml(chatId, formatter.list(summary), listKeyboard(summary));
    }

    private void showHistory(Long chatId, Long userId) {
        java.util.List<YearMonth> months = ot.historyMonths(userId);
        telegram.sendMessage(chatId, formatter.history(months), historyKeyboard(months));
    }

    private void confirmFromToken(Long chatId, Long userId, String token) {
        String[] parts = token.split(":");
        if (parts.length != 2) {
            return;
        }
        YearMonth month = YearMonth.parse(parts[0]);
        int number = Integer.parseInt(parts[1]);
        Optional<com.daymate.bot.entity.OtRecordEntity> record = ot.findRecord(userId, number, month);
        if (record.isEmpty()) {
            telegram.sendMessage(chatId, formatter.recordNotFound(number));
            return;
        }
        askDelete(chatId, record.get());
    }

    private void deleteFromToken(Long chatId, Long userId, String token) {
        String[] parts = token.split(":");
        if (parts.length != 2) {
            return;
        }
        YearMonth month = YearMonth.parse(parts[0]);
        int number = Integer.parseInt(parts[1]);
        try {
            ot.deleteRecord(userId, number, month);
            telegram.sendMessage(chatId, formatter.deleted(number));
            showMonth(chatId, userId, month);
        } catch (IllegalArgumentException exception) {
            telegram.sendMessage(chatId, formatter.recordNotFound(number));
        }
    }

    private void askDelete(Long chatId, com.daymate.bot.entity.OtRecordEntity record) {
        YearMonth month = YearMonth.from(record.getWorkDate());
        String token = month + ":" + record.getRecordNumber();
        telegram.sendMessage(chatId, formatter.confirmDelete(record), List.of(List.of(
                button(Texts.t("Delete", "លុប"), "ot:delok:" + token),
                button(Texts.t("Keep", "ទុក"), "ot:keep:" + month))));
    }

    private List<List<Map<String, String>>> listKeyboard(OtRecordService.MonthSummary summary) {
        List<List<Map<String, String>>> rows = new ArrayList<>();
        for (OtRecordEntity record : summary.records()) {
            YearMonth month = YearMonth.from(record.getWorkDate());
            String token = month + ":" + record.getRecordNumber();
            rows.add(List.of(
                    button("✏️ #" + record.getRecordNumber(), "ot:edit:" + token),
                    button("🗑 #" + record.getRecordNumber(), "ot:del:" + token)));
        }
        rows.add(List.of(button(Texts.t("History", "ប្រវត្តិ"), "ot:hist")));
        return rows;
    }

    private List<List<Map<String, String>>> historyKeyboard(java.util.List<YearMonth> months) {
        List<List<Map<String, String>>> rows = new ArrayList<>();
        for (YearMonth month : months) {
            rows.add(List.of(button(Texts.month(month), "ot:mon:" + month)));
        }
        rows.add(List.of(button(Texts.t("This month", "ខែនេះ"), "ot:mon:" + ot.currentMonth())));
        return rows;
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
        return List.of(List.of(
                button(Texts.t("Yes, ACLEDA", "ចាស ACLEDA"), "ot:acleda:yes"),
                button(Texts.t("No", "ទេ"), "ot:acleda:no")));
    }

    private List<List<Map<String, String>>> homeKeyboard(LocalDate today, int workDays) {
        List<List<Map<String, String>>> rows = new ArrayList<>();
        for (String[] range : SUGGESTED_RANGES) {
            String label = range[0] + "-" + range[1];
            rows.add(List.of(quick(today, range[0], range[1], workDays,
                    Texts.t("Today " + label, "ថ្ងៃនេះ " + label))));
        }
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
        rows.add(List.of(button(Texts.t("❌ Cancel", "❌ បោះបង់"), "ot:cancel")));
        return rows;
    }

    private List<List<Map<String, String>>> editKeyboard(OtRecordEntity record, int workDays) {
        LocalDate date = record.getWorkDate();
        OtShift auto = OtShift.forDate(date, workDays);
        boolean weekend = auto == OtShift.WEEKEND;
        List<List<Map<String, String>>> rows = new ArrayList<>();
        for (String[] range : SUGGESTED_RANGES) {
            rows.add(List.of(editSlot(record, range[0], range[1], auto)));
        }
        if (!weekend) {
            rows.add(List.of(editSlot(record, "08:30", "17:30", OtShift.HOLIDAY)));
            rows.add(List.of(
                    editRate(record, OtShift.EVENING),
                    editRate(record, OtShift.WEEKEND),
                    editRate(record, OtShift.HOLIDAY)));
        }
        rows.add(List.of(
                button(Texts.t("← List", "← បញ្ជី"), "ot:editback:" + YearMonth.from(date)),
                button(Texts.t("❌ Cancel", "❌ បោះបង់"), "ot:cancel")));
        return rows;
    }

    private List<List<Map<String, String>>> timeKeyboard(LocalDate date, int workDays) {
        OtShift auto = OtShift.forDate(date, workDays);
        List<List<Map<String, String>>> rows = new ArrayList<>();
        for (String[] range : SUGGESTED_RANGES) {
            rows.add(List.of(slot(date, range[0], range[1], auto)));
        }
        if (auto != OtShift.WEEKEND) {
            rows.add(List.of(slot(date, "08:30", "17:30", OtShift.HOLIDAY)));
        }
        rows.add(List.of(button(Texts.t("Another day", "ថ្ងៃផ្សេង"), "ot:home"),
                button(Texts.t("❌ Cancel", "❌ បោះបង់"), "ot:cancel")));
        return rows;
    }

    private Map<String, String> quick(LocalDate date, String start, String end, int workDays, String label) {
        return button(label, token(date, start, end, OtShift.forDate(date, workDays)));
    }

    private Map<String, String> slot(LocalDate date, String start, String end, OtShift shift) {
        return button(start + "-" + end + " · " + shiftButtonName(shift), token(date, start, end, shift));
    }

    private Map<String, String> editSlot(OtRecordEntity record, String start, String end, OtShift shift) {
        String data = "ot:edittime:" + YearMonth.from(record.getWorkDate()).format(DateTimeFormatter.ofPattern("yyyyMM"))
                + ":" + record.getRecordNumber()
                + ":" + start.replace(":", "")
                + ":" + end.replace(":", "")
                + ":" + shiftCode(shift);
        return button(start + "-" + end + " · " + shiftButtonName(shift), data);
    }

    private Map<String, String> editRate(OtRecordEntity record, OtShift shift) {
        String label = switch (shift) {
            case HOLIDAY -> Texts.t("Holiday 100%", "ថ្ងៃឈប់ 100%");
            case EVENING -> Texts.t("Evening 150%", "ល្ងាច 150%");
            case WEEKEND -> Texts.t("Weekend 200%", "ចុងសប្តាហ៍ 200%");
        };
        String data = "ot:editrate:" + YearMonth.from(record.getWorkDate())
                + ":" + record.getRecordNumber() + ":" + shiftCode(shift);
        return button(label, data);
    }

    private static String shiftButtonName(OtShift shift) {
        return switch (shift) {
            case HOLIDAY -> Texts.t("Holiday", "ថ្ងៃឈប់");
            case EVENING -> Texts.t("Evening", "ល្ងាច");
            case WEEKEND -> Texts.t("Weekend", "ចុងសប្តាហ៍");
        };
    }

    private static String shiftCode(OtShift shift) {
        return switch (shift) {
            case HOLIDAY -> "H";
            case WEEKEND -> "W";
            case EVENING -> "E";
        };
    }

    private static OtShift shiftFromCode(String code) {
        return switch (code) {
            case "H" -> OtShift.HOLIDAY;
            case "W" -> OtShift.WEEKEND;
            default -> OtShift.EVENING;
        };
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
        return List.of(List.of(
                button(Texts.t("5 days", "5 ថ្ងៃ"), "ot:days:5"),
                button(Texts.t("6 days", "6 ថ្ងៃ"), "ot:days:6")));
    }

    private static List<List<Map<String, String>>> timesKeyboard() {
        return List.of(List.of(
                button(Texts.t("1 time", "1 ដង"), "ot:times:1"),
                button(Texts.t("2 times", "2 ដង"), "ot:times:2")));
    }

    private static List<List<Map<String, String>>> cancelKeyboard() {
        return List.of(List.of(button("❌ Cancel", "ot:cancel")));
    }

    private static List<List<Map<String, String>>> changeKeyboard() {
        return List.of(List.of(button(Texts.t("Change salary", "ប្ដូរប្រាក់ខែ"), "ot:change")));
    }

    private static Map<String, String> button(String text, String data) {
        return Map.of("text", text, "callback_data", data);
    }

    private record EditTarget(YearMonth month, int number) {
    }
}
