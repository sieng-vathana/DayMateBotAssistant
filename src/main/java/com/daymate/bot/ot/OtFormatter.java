package com.daymate.bot.ot;

import com.daymate.bot.i18n.Texts;
import com.daymate.bot.entity.OtRecordEntity;
import com.daymate.bot.entity.SalaryProfileEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

@Component
public class OtFormatter {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter LIST_DAY = DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.ENGLISH);

    public String oneLinePrompt(java.time.LocalDate today) {
        String month = Texts.monthLong(today);
        int day = today.getDayOfMonth();
        return Texts.t(
                "Record OT for " + month + ". Day comes first.\n\n"
                        + "One line saves it:\n<b>" + day + " 08:30-17:30</b>\n<b>" + day + " 08:30-17:30 holiday</b>\n\n"
                        + "Weekday = Evening 150%. Sat/Sun = Weekend 200%.\n"
                        + "Add holiday only for Holiday 100%.\n\n"
                        + "Or tap a day, then one time. That second tap saves it.",
                "កត់ម៉ោងបន្ថែមសម្រាប់ " + month + "។ ថ្ងៃមុនគេ។\n\n"
                        + "សរសេរមួយជួររួចផ្ញើ៖\n<b>" + day + " 08:30-17:30</b>\n<b>" + day + " 08:30-17:30 holiday</b>\n\n"
                        + "ថ្ងៃធ្វើការ = ល្ងាច 150%។ សៅរ៍/អាទិត្យ = ចុងសប្តាហ៍ 200%។\n"
                        + "បន្ថែម holiday សម្រាប់ថ្ងៃឈប់សម្រាក 100%។\n\n"
                        + "ឬចុចថ្ងៃ បន្ទាប់មកចុចម៉ោងមួយ។ ការចុចនោះរក្សាទុកភ្លាម។");
    }

    public String invalidEntry() {
        return Texts.t(
                "I couldn't read that. Day first, then the time.\nExample: <b>24 08:30-17:30</b> or <b>24 08:30-17:30 holiday</b>.",
                "ខ្ញុំអានមិនបានទេ។ ថ្ងៃមុន បន្ទាប់មកម៉ោង។\nឧទាហរណ៍៖ <b>24 08:30-17:30</b> ឬ <b>24 08:30-17:30 holiday</b>។");
    }

    public String dayChosen(java.time.LocalDate date, boolean weekend) {
        String sample = weekend
                ? "<b>08:30-17:30</b>"
                : "<b>08:30-17:30</b>\n<b>08:30-17:30 holiday</b>";
        return Texts.t(
                "📅 " + Texts.day(date) + "\n\nTap a time, or type your own for this day:\n" + sample,
                "📅 " + Texts.day(date) + "\n\nចុចម៉ោង ឬវាយម៉ោងផ្ទាល់សម្រាប់ថ្ងៃនេះ៖\n" + sample);
    }

    public String invalidTimeForDay(boolean weekend) {
        String sample = weekend
                ? "<b>08:30-17:30</b>"
                : "<b>08:30-17:30</b> or <b>08:30-17:30 holiday</b>";
        String khmer = weekend
                ? "<b>08:30-17:30</b>"
                : "<b>08:30-17:30</b> ឬ <b>08:30-17:30 holiday</b>";
        return Texts.t(
                "Type the time for this day. Example: " + sample + ".",
                "វាយម៉ោងសម្រាប់ថ្ងៃនេះ។ ឧទាហរណ៍៖ " + khmer + "។");
    }

    public String acledaPrompt() {
        return Texts.t("""
                Before recording OT, set your base salary.

                Are you an ACLEDA employee?
                ACLEDA works 5 days a week (Monday–Friday).
                Saturday and Sunday are weekend OT at 200%.
                Pay is 2 times a month, on day 12 and day 26.""", """
                មុនកត់ម៉ោងបន្ថែម សូមកំណត់ប្រាក់ខែគោលសិន។

                តើអ្នកជាបុគ្គលិក ACLEDA ទេ?
                ACLEDA ធ្វើការ 5 ថ្ងៃក្នុងមួយសប្តាហ៍ (ចន្ទ–សុក្រ)។
                សៅរ៍ និងអាទិត្យ ជាម៉ោងបន្ថែមចុងសប្តាហ៍ 200%។
                ប្រាក់ខែបើក 2 ដងក្នុងមួយខែ នៅថ្ងៃទី 12 និង 26។""");
    }

    public String workDaysPrompt() {
        return Texts.t("""
                How many days do you work each week?

                5 days — Monday to Friday. Saturday and Sunday are weekend OT (200%). Rate uses 5 × 52 ÷ 12 × 8 hours.
                6 days — Monday to Saturday. Only Sunday is weekend OT (200%). Rate uses 6 × 52 ÷ 12 × 8 hours.""", """
                អ្នកធ្វើការប៉ុន្មានថ្ងៃក្នុងមួយសប្តាហ៍?

                5 ថ្ងៃ — ចន្ទដល់សុក្រ។ សៅរ៍ និងអាទិត្យ ជាចុងសប្តាហ៍ (200%)។ អត្រាប្រើ 5 × 52 ÷ 12 × 8 ម៉ោង។
                6 ថ្ងៃ — ចន្ទដល់សៅរ៍។ មានតែអាទិត្យទេដែលជាចុងសប្តាហ៍ (200%)។ អត្រាប្រើ 6 × 52 ÷ 12 × 8 ម៉ោង។""");
    }

    public String payTimesPrompt() {
        return Texts.t(
                "How many times is salary paid each month?",
                "ប្រាក់ខែបើកប៉ុន្មានដងក្នុងមួយខែ?");
    }

    public String payDayPrompt(int which, int total) {
        if (total == 1) {
            return Texts.t("""
                    Which day of the month does salary arrive?
                    Send the day only, from 1 to 31. Example: 25""", """
                    ប្រាក់ខែមកដល់ថ្ងៃទីប៉ុន្មាន?
                    ផ្ញើតែលេខថ្ងៃ ពី 1 ដល់ 31។ ឧទាហរណ៍៖ 25""");
        }
        String label = which == 1
                ? Texts.t("First", "ទីមួយ")
                : Texts.t("Second", "ទីពីរ");
        return Texts.t(
                label + " payday of the month?\nSend the day only, from 1 to 31.",
                "ថ្ងៃបើកប្រាក់ខែ" + label + "?\nផ្ញើតែលេខថ្ងៃ ពី 1 ដល់ 31។");
    }

    public String salaryAmountPrompt(boolean acleda, int payTimes, int payDay1, Integer payDay2) {
        String schedule = acleda
                ? Texts.t(
                "5-day week. Paid 2 times a month, on day 12 and day 26.",
                "សប្តាហ៍ 5 ថ្ងៃ។ បើក 2 ដងក្នុងមួយខែ នៅថ្ងៃទី 12 និង 26។")
                : scheduleText(payTimes, payDay1, payDay2);
        return schedule + "\n\n" + Texts.t(
                "What is your base salary for the month? (USD)\nExample: 400",
                "ប្រាក់ខែគោលក្នុងមួយខែប៉ុន្មាន? (ដុល្លារ)\nឧទាហរណ៍៖ 400");
    }

    public String salarySaved(SalaryProfileEntity profile) {
        int week = profile.resolvedWorkDaysPerWeek();
        BigDecimal monthHours = OtCalculator.hoursPerMonth(week);
        BigDecimal hourly = profile.getMonthlySalary().divide(monthHours, 10, RoundingMode.HALF_UP);
        BigDecimal shownHourly = hourly.setScale(2, RoundingMode.HALF_UP);
        BigDecimal holiday = shownHourly;
        BigDecimal evening = hourly.multiply(new BigDecimal("1.5")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal weekend = hourly.multiply(new BigDecimal("2")).setScale(2, RoundingMode.HALF_UP);
        String hoursLabel = monthHours.setScale(2, RoundingMode.HALF_UP).toPlainString();
        String weekLine = Texts.t(
                week + "-day week · " + week + " × 52 ÷ 12 × 8h = " + hoursLabel + "h/month",
                "សប្តាហ៍ " + week + " ថ្ងៃ · " + week + " × 52 ÷ 12 × 8h = " + hoursLabel + "h/ខែ");
        return Texts.t("✅ Salary saved\n", "✅ បានរក្សាទុកប្រាក់ខែ\n")
                + weekLine + "\n"
                + scheduleText(profile.getPayTimes(), profile.getPayDay1(), profile.getPayDay2()) + "\n\n"
                + Texts.t("Hourly pay | Base salary", "ប្រាក់ម៉ោង | ប្រាក់ខែគោល") + "\n"
                + money(shownHourly) + " | " + money(profile.getMonthlySalary()) + "\n\n"
                + Texts.t("Holiday", "ថ្ងៃឈប់សម្រាក") + " 100% " + money(holiday) + "/h\n"
                + Texts.t("Evening", "ល្ងាច") + " 150% " + money(evening) + "/h\n"
                + Texts.t("Weekend", "ចុងសប្តាហ៍") + " 200% " + money(weekend) + "/h";
    }

    public String typePrompt() {
        return """
                Which rate is this OT?

                Holiday 100% · Evening 150% · Weekend 200%""";
    }

    public String upcoming(OtRecordService.UpcomingSalary upcoming) {
        StringBuilder text = new StringBuilder(Texts.t("📅 Upcoming salary\n\n", "📅 ប្រាក់ខែខាងមុខ\n\n"));
        text.append(Texts.t("Hourly pay | Base salary", "ប្រាក់ម៉ោង | ប្រាក់ខែគោល")).append('\n');
        text.append(money(upcoming.hourly())).append(" | ").append(money(upcoming.profile().getMonthlySalary()));
        if (upcoming.next().isEmpty()) {
            text.append(Texts.t("\n\nNo payday found.", "\n\nរកមិនឃើញថ្ងៃបើកប្រាក់ខែទេ។"));
            return text.toString();
        }
        boolean acleda = upcoming.profile().isAcledaEmployee();
        appendPayday(text, upcoming.next().get(0), acleda, true);
        if (upcoming.next().size() > 1) {
            appendPayday(text, upcoming.next().get(1), acleda, false);
        }
        return text.toString();
    }

    private void appendPayday(StringBuilder text, OtRecordService.Payday payday, boolean acleda, boolean first) {
        text.append(first
                ? Texts.t("\n\nNext: ", "\n\nបន្ទាប់៖ ")
                : Texts.t("\n\nAfter that: ", "\n\nបន្ទាប់ទៀត៖ "));
        text.append(Texts.day(payday.date()));
        if (!acleda) {
            text.append(Texts.t("\nThis payday: ", "\nប្រាក់ខែលើកនេះ៖ ")).append(money(payday.amount()));
            return;
        }
        text.append(Texts.t("\nBase: ", "\nប្រាក់ខែគោល: ")).append(money(payday.amount()));
        text.append(Texts.t("\nOT paid: ", "\nប្រាក់ OT: "))
                .append(hours(payday.otHours())).append(" · ").append(money(payday.ot()));
        text.append(Texts.t("\nTotal income: ", "\nចំណូលសរុប: ")).append(money(payday.total()));
    }

    public String paydayReminder(OtRecordService.PaydayReminder reminder) {
        String figures = "OT: " + hours(reminder.otHours()) + " · " + money(reminder.ot()) + "\n"
                + Texts.t("Base: ", "ប្រាក់ខែគោល: ") + money(reminder.base()) + "\n"
                + Texts.t("Total income: ", "ចំណូលសរុប: ") + money(reminder.total());
        return Texts.t(
                "Payday tomorrow, " + Texts.day(reminder.payday()) + "\n" + figures
                        + "\n\nSend /otlist if a record needs a change.",
                "ថ្ងៃបើកប្រាក់ខែស្អែក " + Texts.day(reminder.payday()) + "\n" + figures
                        + "\n\nផ្ញើ /otlist ប្រសិនបើកំណត់ត្រាត្រូវកែ។");
    }

    public String salarySummary(SalaryProfileEntity profile) {
        return salarySaved(profile) + "\n\n" + Texts.t(
                "Send /ot to record overtime, or tap Change to update this.",
                "ផ្ញើ /ot ដើម្បីកត់ម៉ោងបន្ថែម ឬចុច ប្ដូរប្រាក់ខែ ដើម្បីកែ។");
    }

    public String needSalaryFirst() {
        return Texts.t(
                "Set your base salary before recording OT.",
                "សូមកំណត់ប្រាក់ខែគោលសិន មុនកត់ម៉ោងបន្ថែម។");
    }

    public String chooseYesNo() {
        return Texts.t("Choose Yes or No.", "សូមជ្រើស ចាស ឬ ទេ។");
    }

    public String differentPaydays() {
        return Texts.t(
                "The two paydays must be different days.",
                "ថ្ងៃបើកប្រាក់ខែទាំងពីរត្រូវខុសគ្នា។");
    }

    public String saved(OtRecordService.SavedOt saved) {
        OtRecordEntity record = saved.record();
        return Texts.t("✅ Saved OT #", "✅ បានរក្សាទុក OT #") + record.getRecordNumber() + "\n"
                + line(record) + "\n"
                + clockLine(record) + "\n\n"
                + monthLine(saved.month());
    }

    public String updated(OtRecordService.SavedOt saved) {
        OtRecordEntity record = saved.record();
        return Texts.t("✅ Updated OT #", "✅ បានកែ OT #") + record.getRecordNumber() + "\n"
                + line(record) + "\n"
                + clockLine(record) + "\n\n"
                + monthLine(saved.month());
    }

    public String editPrompt(OtRecordEntity record, boolean weekend) {
        String sample = weekend
                ? "<b>08:30-17:30</b>"
                : "<b>08:30-17:30</b>\n<b>08:30-17:30 holiday</b>";
        String english = weekend
                ? "The day stays the same. This day is weekend OT at 200%.\nTap a time, or type one:\n"
                : "The day stays the same. Tap a new time, or a rate to keep these hours.\nOr type a time:\n";
        String khmer = weekend
                ? "ថ្ងៃនៅដដែល។ ថ្ងៃនេះជាម៉ោងបន្ថែមចុងសប្តាហ៍ 200%។\nចុចម៉ោង ឬវាយម៉ោង៖\n"
                : "ថ្ងៃនៅដដែល។ ចុចម៉ោងថ្មី ឬអត្រា ដើម្បីរក្សាម៉ោងនេះ។\nឬវាយម៉ោង៖\n";
        return Texts.t(
                "✏️ Edit OT #" + record.getRecordNumber() + "\n"
                        + Texts.day(record.getWorkDate()) + "\n"
                        + clockLine(record) + "\n\n"
                        + english + sample,
                "✏️ កែ OT #" + record.getRecordNumber() + "\n"
                        + Texts.day(record.getWorkDate()) + "\n"
                        + clockLine(record) + "\n\n"
                        + khmer + sample);
    }

    public String list(OtRecordService.MonthSummary summary) {
        String title = Texts.t("📋 OT records — ", "📋 កំណត់ត្រា OT — ") + Texts.month(summary.month());
        if (summary.records().isEmpty()) {
            return title + Texts.t(
                    "\n\nNo OT yet this month. Send /ot to add one.",
                    "\n\nមិនទាន់មានម៉ោងបន្ថែមខែនេះទេ។ ផ្ញើ /ot ដើម្បីបន្ថែម។");
        }
        StringBuilder text = new StringBuilder(title);
        for (OtRecordEntity record : summary.records()) {
            text.append("\n\n").append(listRow(record));
        }
        text.append("\n\n").append(monthLabel(summary)).append(" · <b>").append(money(summary.amount())).append("</b>");
        text.append(Texts.t(
                "\n\nTap ✏️ to change one, 🗑 to delete, or send /del 3.",
                "\n\nចុច ✏️ ដើម្បីកែ, 🗑 ដើម្បីលុប ឬផ្ញើ /del 3។"));
        return text.toString();
    }

    public String history(java.util.List<java.time.YearMonth> months) {
        if (months.isEmpty()) {
            return Texts.t(
                    "🗂 OT history\n\nNo OT yet. Send /ot to add one.",
                    "🗂 ប្រវត្តិ OT\n\nមិនទាន់មានម៉ោងបន្ថែមទេ។ ផ្ញើ /ot ដើម្បីបន្ថែម។");
        }
        return Texts.t(
                "🗂 OT history\n\nTap a month, or send /ot-history 2026-10",
                "🗂 ប្រវត្តិ OT\n\nចុចខែមួយ ឬផ្ញើ /ot-history 2026-10");
    }

    public String confirmDelete(OtRecordEntity record) {
        return Texts.t("Delete OT #", "លុប OT #") + record.getRecordNumber() + "?\n"
                + Texts.day(record.getWorkDate()) + "\n"
                + clockLine(record);
    }

    public String deleted(int recordNumber) {
        return Texts.t("Deleted OT #", "បានលុប OT #") + recordNumber + ".";
    }

    public String recordNotFound(int recordNumber) {
        return Texts.t("OT #" + recordNumber + " was not found.", "រកមិនឃើញ OT #" + recordNumber + " ទេ។");
    }

    public String deleteUsage() {
        return Texts.t(
                "Send /del 3 to delete OT #3. Add a month if you want: /del 3 2026-10",
                "ផ្ញើ /del 3 ដើម្បីលុប OT #3។ បន្ថែមខែបើចង់៖ /del 3 2026-10");
    }

    public String invalidMonth() {
        return Texts.t(
                "Send a month like <b>2026-10</b>, <b>10</b>, or <b>October</b>.",
                "ផ្ញើខែដូច <b>2026-10</b>, <b>10</b>, ឬ <b>តុលា</b>។");
    }

    public String cancelled() {
        return Texts.t(
                "Cancelled. Send /ot when you want to record overtime.",
                "បានបោះបង់។ ផ្ញើ /ot ពេលចង់កត់ម៉ោងបន្ថែម។");
    }

    public String invalidDay() {
        return Texts.t(
                "Send a day of the month from 1 to 31. Example: 12",
                "ផ្ញើលេខថ្ងៃក្នុងខែ ពី 1 ដល់ 31។ ឧទាហរណ៍៖ 12");
    }

    public String invalidSalary() {
        return Texts.t(
                "Send the monthly base salary in USD. Example: 400",
                "ផ្ញើប្រាក់ខែគោលជាដុល្លារ។ ឧទាហរណ៍៖ 400");
    }

    private String listRow(OtRecordEntity record) {
        String hours = hours(record.getPaidHours());
        if (record.getBreakHours().signum() > 0) {
            String breakHours = hours(record.getBreakHours());
            if (breakHours.endsWith(".00h")) {
                breakHours = breakHours.substring(0, breakHours.length() - 4) + "h";
            }
            hours += "+" + breakHours;
        }
        return "#" + record.getRecordNumber()
                + " " + listDay(record.getWorkDate())
                + " " + shiftName(record) + " " + record.getShift().percent() + "%"
                + " " + CLOCK.format(record.getStartTime()) + "-" + CLOCK.format(record.getEndTime())
                + " " + hours
                + " " + money(record.getHourlyRate()) + "=" + money(record.getAmount());
    }

    private String listDay(java.time.LocalDate date) {
        if (com.daymate.bot.i18n.LanguageContext.get() == com.daymate.bot.domain.Language.KM) {
            return Texts.day(date).replace(" " + date.getYear(), "");
        }
        return LIST_DAY.format(date);
    }

    private String line(OtRecordEntity record) {
        return shiftLabel(record) + " · " + Texts.day(record.getWorkDate());
    }

    private String clockLine(OtRecordEntity record) {
        return detailLine(record) + " = " + money(record.getAmount());
    }

    private String detailLine(OtRecordEntity record) {
        String hours = record.getBreakHours().signum() > 0
                ? hours(record.getPaidHours()) + Texts.t(
                " (incl. " + hours(record.getBreakHours()) + " break)",
                " (រួមសម្រាក " + hours(record.getBreakHours()) + ")")
                : hours(record.getPaidHours()) + Texts.t(" paid", " បានបង់");
        return CLOCK.format(record.getStartTime()) + "-" + CLOCK.format(record.getEndTime())
                + " → " + hours
                + " × " + money(record.getHourlyRate()) + "/h";
    }

    private String monthLine(OtRecordService.MonthSummary summary) {
        return monthLabel(summary) + " · " + money(summary.amount());
    }

    private String monthLabel(OtRecordService.MonthSummary summary) {
        return "📈 " + Texts.month(summary.month()) + Texts.t(" so far: ", " មកទល់ពេលនេះ៖ ")
                + hours(summary.paidHours());
    }

    private String shiftName(OtRecordEntity record) {
        return switch (record.getShift()) {
            case HOLIDAY -> Texts.t("Holiday", "ថ្ងៃឈប់សម្រាក");
            case EVENING -> Texts.t("Evening", "ល្ងាច");
            case WEEKEND -> Texts.t("Weekend", "ចុងសប្តាហ៍");
        };
    }

    private String shiftLabel(OtRecordEntity record) {
        return shiftName(record) + " (" + record.getShift().percent() + "%)";
    }

    private String scheduleText(int payTimes, int payDay1, Integer payDay2) {
        if (payTimes == 2 && payDay2 != null) {
            return Texts.t(
                    "Paid 2 times a month, on day " + payDay1 + " and day " + payDay2 + ".",
                    "បើក 2 ដងក្នុងមួយខែ នៅថ្ងៃទី " + payDay1 + " និងថ្ងៃទី " + payDay2 + "។");
        }
        return Texts.t(
                "Paid 1 time a month, on day " + payDay1 + ".",
                "បើក 1 ដងក្នុងមួយខែ នៅថ្ងៃទី " + payDay1 + "។");
    }

    public static String money(BigDecimal value) {
        return "$" + value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public static String hours(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString() + "h";
    }
}
