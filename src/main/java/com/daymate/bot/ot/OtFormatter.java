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

    public String timePrompt() {
        return """
                🕐 Start and finish time (HH:MM)?

                Format: 09:00-17:00 (dash separated).""";
    }

    public String invalidTime() {
        return "I couldn't read that. Use <b>HH:MM-HH:MM</b>, e.g. <b>09:00-17:00</b>.";
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

                5 days — Monday to Friday. Saturday and Sunday are weekend OT (200%).
                6 days — Monday to Saturday. Only Sunday is weekend OT (200%).
                The hourly rate always uses 26 days × 8 hours.""";
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
                ? "ACLEDA · 5-day week · pay days 12 and 26 (2 times a month)."
                : scheduleText(payTimes, payDay1, payDay2);
        return schedule + "\n\nWhat is your base salary for the month? (USD)\nExample: 480";
    }

    public String salarySaved(SalaryProfileEntity profile) {
        int week = profile.resolvedWorkDaysPerWeek();
        BigDecimal monthHours = OtCalculator.hoursPerMonth(week);
        BigDecimal hourly = profile.getMonthlySalary().divide(monthHours, 10, RoundingMode.HALF_UP);
        BigDecimal evening = hourly.multiply(new BigDecimal("1.5")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal weekend = hourly.multiply(new BigDecimal("2")).setScale(2, RoundingMode.HALF_UP);
        String weekLine = week + "-day week · rate uses 26 days × 8h = "
                + monthHours.toPlainString() + "h/month";
        return "✅ Salary saved\n"
                + (profile.isAcledaEmployee() ? "ACLEDA employee\n" : "")
                + weekLine + "\n"
                + scheduleText(profile.getPayTimes(), profile.getPayDay1(), profile.getPayDay2()) + "\n"
                + "Base salary: " + money(profile.getMonthlySalary()) + "\n"
                + "Hourly " + money(hourly.setScale(2, RoundingMode.HALF_UP))
                + " · Evening 150% " + money(evening) + "/h"
                + " · Weekend 200% " + money(weekend) + "/h";
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
