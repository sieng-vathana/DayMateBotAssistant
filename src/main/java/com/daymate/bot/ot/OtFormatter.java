package com.daymate.bot.ot;

import com.daymate.bot.entity.OtRecordEntity;
import com.daymate.bot.entity.SalaryProfileEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class OtFormatter {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    public String oneLinePrompt(java.time.LocalDate today) {
        String month = today.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.ENGLISH));
        return "Record OT for " + month + ". Day comes first.\n\n"
                + "One line saves it:\n"
                + "<b>" + today.getDayOfMonth() + " 16:30-17:30</b>\n"
                + "<b>" + today.getDayOfMonth() + " 16:30-17:30 holiday</b>\n\n"
                + "Weekday = Evening 150%. Sat/Sun = Weekend 200%.\n"
                + "Add holiday only for Holiday 100%.\n\n"
                + "Or tap a day, then one time. That second tap saves it.";
    }

    public String invalidEntry() {
        return "I couldn't read that. Day first, then the time.\n"
                + "Example: <b>24 16:30-17:30</b> or <b>24 16:30-17:30 holiday</b>.";
    }

    public String dayChosen(java.time.LocalDate date) {
        return "📅 " + DAY.format(date) + "\n\nOne tap saves this day.";
    }

    public String acledaPrompt() {
        return """
                Before recording OT, set your base salary.

                Are you an ACLEDA employee?
                ACLEDA works 5 days a week (Monday–Friday).
                Saturday and Sunday are weekend OT at 200%.
                Pay is 2 times a month, on day 12 and day 26.""";
    }

    public String workDaysPrompt() {
        return """
                How many days do you work each week?

                5 days — Monday to Friday. Saturday and Sunday are weekend OT (200%). Rate uses 5 × 52 ÷ 12 × 8 hours.
                6 days — Monday to Saturday. Only Sunday is weekend OT (200%). Rate uses 6 × 52 ÷ 12 × 8 hours.""";
    }

    public String payTimesPrompt() {
        return "How many times is salary paid each month?";
    }

    public String payDayPrompt(int which, int total) {
        if (total == 1) {
            return """
                    Which day of the month does salary arrive?
                    Send the day only, from 1 to 31. Example: 25""";
        }
        String label = which == 1 ? "First" : "Second";
        return label + " payday of the month?\nSend the day only, from 1 to 31.";
    }

    public String salaryAmountPrompt(boolean acleda, int payTimes, int payDay1, Integer payDay2) {
        String schedule = acleda
                ? "5-day week. Paid 2 times a month, on day 12 and day 26."
                : scheduleText(payTimes, payDay1, payDay2);
        return schedule + "\n\nWhat is your base salary for the month? (USD)\nExample: 400";
    }

    public String salarySaved(SalaryProfileEntity profile) {
        int week = profile.resolvedWorkDaysPerWeek();
        BigDecimal monthHours = OtCalculator.hoursPerMonth(week);
        BigDecimal hourly = profile.getMonthlySalary().divide(monthHours, 10, RoundingMode.HALF_UP);
        BigDecimal shownHourly = hourly.setScale(2, RoundingMode.HALF_UP);
        BigDecimal holiday = shownHourly;
        BigDecimal evening = hourly.multiply(new BigDecimal("1.5")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal weekend = hourly.multiply(new BigDecimal("2")).setScale(2, RoundingMode.HALF_UP);
        String weekLine = week + "-day week · " + week + " × 52 ÷ 12 × 8h = "
                + monthHours.setScale(2, RoundingMode.HALF_UP).toPlainString() + "h/month";
        return "✅ Salary saved\n"
                + weekLine + "\n"
                + scheduleText(profile.getPayTimes(), profile.getPayDay1(), profile.getPayDay2()) + "\n\n"
                + "Hourly pay | Base salary\n"
                + money(shownHourly) + " | " + money(profile.getMonthlySalary()) + "\n\n"
                + "Holiday 100% " + money(holiday) + "/h\n"
                + "Evening 150% " + money(evening) + "/h\n"
                + "Weekend 200% " + money(weekend) + "/h";
    }

    public String typePrompt() {
        return """
                Which rate is this OT?

                Holiday 100% · Evening 150% · Weekend 200%""";
    }

    public String upcoming(OtRecordService.UpcomingSalary upcoming) {
        StringBuilder text = new StringBuilder("📅 Upcoming salary\n\n");
        text.append("Hourly pay | Base salary\n");
        text.append(money(upcoming.hourly())).append(" | ").append(money(upcoming.profile().getMonthlySalary()));
        if (upcoming.next().isEmpty()) {
            text.append("\n\nNo payday found.");
            return text.toString();
        }
        text.append("\n\nNext: ").append(DAY.format(upcoming.next().get(0).date()));
        text.append("\nThis payday: ").append(money(upcoming.next().get(0).amount()));
        if (upcoming.next().size() > 1) {
            text.append("\n\nAfter that: ").append(DAY.format(upcoming.next().get(1).date()));
            text.append("\nThis payday: ").append(money(upcoming.next().get(1).amount()));
        }
        return text.toString();
    }

    public String salarySummary(SalaryProfileEntity profile) {
        return salarySaved(profile) + "\n\nSend /ot to record overtime, or tap Change to update this.";
    }

    public String needSalaryFirst() {
        return "Set your base salary before recording OT.";
    }

    public String saved(OtRecordService.SavedOt saved) {
        OtRecordEntity record = saved.record();
        return "✅ Saved OT #" + record.getRecordNumber() + "\n"
                + line(record) + "\n"
                + clockLine(record) + "\n\n"
                + monthLine(saved.month());
    }

    public String list(OtRecordService.MonthSummary summary) {
        String title = "📋 OT records — " + MONTH.format(summary.month());
        if (summary.records().isEmpty()) {
            return title + "\n\nNo OT yet this month. Send /ot to add one.";
        }
        StringBuilder text = new StringBuilder(title);
        for (OtRecordEntity record : summary.records()) {
            text.append("\n\n#").append(record.getRecordNumber())
                    .append(" · ").append(DAY.format(record.getWorkDate()))
                    .append(" · ").append(shiftLabel(record))
                    .append('\n').append(clockLine(record));
        }
        text.append("\n\n").append(monthLine(summary));
        return text.toString();
    }

    public String cancelled() {
        return "Cancelled. Send /ot when you want to record overtime.";
    }

    public String invalidDay() {
        return "Send a day of the month from 1 to 31. Example: 12";
    }

    public String invalidSalary() {
        return "Send the monthly base salary in USD. Example: 480";
    }

    private String line(OtRecordEntity record) {
        return shiftLabel(record) + " · " + DAY.format(record.getWorkDate());
    }

    private String clockLine(OtRecordEntity record) {
        String hours = record.getBreakHours().signum() > 0
                ? hours(record.getPaidHours()) + " (incl. " + hours(record.getBreakHours()) + " break)"
                : hours(record.getPaidHours()) + " paid";
        return CLOCK.format(record.getStartTime()) + "-" + CLOCK.format(record.getEndTime())
                + " → " + hours
                + " × " + money(record.getHourlyRate()) + "/h = " + money(record.getAmount());
    }

    private String monthLine(OtRecordService.MonthSummary summary) {
        return "📈 " + MONTH.format(summary.month()) + " so far: "
                + hours(summary.paidHours()) + " · " + money(summary.amount());
    }

    private String shiftLabel(OtRecordEntity record) {
        return record.getShift().label() + " (" + record.getShift().percent() + "%)";
    }

    private String scheduleText(int payTimes, int payDay1, Integer payDay2) {
        if (payTimes == 2 && payDay2 != null) {
            return "Paid 2 times a month, on day " + payDay1 + " and day " + payDay2 + ".";
        }
        return "Paid 1 time a month, on day " + payDay1 + ".";
    }

    public static String money(BigDecimal value) {
        return "$" + value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public static String hours(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString() + "h";
    }
}
