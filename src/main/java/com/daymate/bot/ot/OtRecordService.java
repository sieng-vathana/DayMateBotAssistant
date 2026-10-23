package com.daymate.bot.ot;

import com.daymate.bot.domain.OtTimeRange;
import com.daymate.bot.entity.OtRecordEntity;
import com.daymate.bot.entity.SalaryProfileEntity;
import com.daymate.bot.repository.OtRecordRepository;
import com.daymate.bot.repository.SalaryProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
public class OtRecordService {
    private final SalaryProfileRepository salaries;
    private final OtRecordRepository records;
    private final Clock clock;
    private final ZoneId zoneId;

    public OtRecordService(SalaryProfileRepository salaries, OtRecordRepository records, Clock clock, ZoneId zoneId) {
        this.salaries = salaries;
        this.records = records;
        this.clock = clock;
        this.zoneId = zoneId;
    }

    @Transactional(readOnly = true)
    public Optional<SalaryProfileEntity> salary(Long userId) {
        return salaries.findById(userId);
    }

    @Transactional
    public SalaryProfileEntity saveSalary(Long userId, boolean acleda, int payTimes, int payDay1, Integer payDay2,
                                          int workDaysPerWeek, BigDecimal monthlySalary) {
        if (payTimes != 1 && payTimes != 2) {
            throw new IllegalArgumentException("Salary can be paid 1 or 2 times a month.");
        }
        requireDay(payDay1);
        if (payTimes == 2) {
            requireDay(payDay2);
            if (payDay1 == payDay2) {
                throw new IllegalArgumentException("The two paydays must be different days.");
            }
        } else {
            payDay2 = null;
        }
        if (monthlySalary == null || monthlySalary.signum() <= 0) {
            throw new IllegalArgumentException("Base salary must be greater than 0.");
        }
        int week = acleda ? 5 : workDaysPerWeek;
        SalaryProfileEntity profile = new SalaryProfileEntity(
                userId, acleda, payTimes, payDay1, payDay2, week,
                monthlySalary.setScale(2, java.math.RoundingMode.HALF_UP),
                Instant.now(clock));
        return salaries.save(profile);
    }

    @Transactional
    public SavedOt record(Long userId, Long chatId, LocalDate workDate, OtTimeRange range, com.daymate.bot.domain.OtShift shift) {
        SalaryProfileEntity profile = salaries.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Set your base salary before recording OT."));
        OtCalculator.Quote quote = OtCalculator.quote(
                profile.getMonthlySalary(), range.start(), range.end(), shift, profile.resolvedWorkDaysPerWeek());
        int number = nextNumber(userId);
        OtRecordEntity saved = records.save(new OtRecordEntity(
                userId, chatId, number, workDate, range.start(), range.end(), quote.shift(),
                quote.grossHours(), quote.breakHours(), quote.paidHours(), quote.hourlyRate(), quote.amount(),
                Instant.now(clock)));
        MonthSummary month = monthSummary(userId, YearMonth.from(workDate));
        return new SavedOt(saved, month);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    @Transactional(readOnly = true)
    public MonthSummary monthSummary(Long userId, YearMonth month) {
        List<OtRecordEntity> rows = records.findByTelegramUserIdAndWorkDateBetweenOrderByWorkDateAscRecordNumberAsc(
                userId, month.atDay(1), month.atEndOfMonth());
        BigDecimal hours = BigDecimal.ZERO.setScale(2);
        BigDecimal amount = BigDecimal.ZERO.setScale(2);
        for (OtRecordEntity row : rows) {
            hours = hours.add(row.getPaidHours());
            amount = amount.add(row.getAmount());
        }
        return new MonthSummary(month, rows, hours, amount);
    }

    public YearMonth currentMonth() {
        return YearMonth.now(clock);
    }

    @Transactional(readOnly = true)
    public List<YearMonth> historyMonths(Long userId) {
        java.util.LinkedHashSet<YearMonth> months = new java.util.LinkedHashSet<>();
        for (OtRecordEntity record : records.findByTelegramUserIdOrderByWorkDateDesc(userId)) {
            months.add(YearMonth.from(record.getWorkDate()));
        }
        return List.copyOf(months);
    }

    @Transactional(readOnly = true)
    public Optional<OtRecordEntity> findRecord(Long userId, int recordNumber) {
        return records.findByTelegramUserIdAndRecordNumber(userId, recordNumber);
    }

    @Transactional
    public OtRecordEntity deleteRecord(Long userId, int recordNumber, YearMonth month) {
        OtRecordEntity record = records.findByTelegramUserIdAndRecordNumber(userId, recordNumber)
                .orElseThrow(() -> new IllegalArgumentException("OT #" + recordNumber + " was not found."));
        if (month != null && !YearMonth.from(record.getWorkDate()).equals(month)) {
            throw new IllegalArgumentException("OT #" + recordNumber + " is not in " + month + ".");
        }
        records.delete(record);
        return record;
    }

    public ZoneId zoneId() {
        return zoneId;
    }

    @Transactional(readOnly = true)
    public UpcomingSalary upcoming(Long userId) {
        SalaryProfileEntity profile = salaries.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Set your base salary before checking the next payday."));
        LocalDate today = LocalDate.now(clock);
        java.util.ArrayList<LocalDate> dates = new java.util.ArrayList<>();
        YearMonth month = YearMonth.from(today);
        YearMonth limit = month.plusMonths(14);
        while (dates.size() < 4 && month.isBefore(limit)) {
            for (int day : payDays(profile)) {
                LocalDate date = month.atDay(Math.min(day, month.lengthOfMonth()));
                if (!date.isBefore(today) && !dates.contains(date)) {
                    dates.add(date);
                }
            }
            month = month.plusMonths(1);
        }
        dates.sort(java.util.Comparator.naturalOrder());
        if (dates.size() > 2) {
            dates = new java.util.ArrayList<>(dates.subList(0, 2));
        }
        BigDecimal hourly = profile.getMonthlySalary().divide(
                OtCalculator.hoursPerMonth(profile.resolvedWorkDaysPerWeek()), 10, java.math.RoundingMode.HALF_UP);
        BigDecimal each = profile.getMonthlySalary().divide(
                BigDecimal.valueOf(Math.max(1, profile.getPayTimes())), 2, java.math.RoundingMode.HALF_UP);
        java.util.ArrayList<Payday> pays = new java.util.ArrayList<>();
        for (LocalDate date : dates) {
            pays.add(new Payday(date, each));
        }
        return new UpcomingSalary(profile, hourly.setScale(2, java.math.RoundingMode.HALF_UP), pays);
    }

    private static int[] payDays(SalaryProfileEntity profile) {
        if (profile.getPayTimes() == 2 && profile.getPayDay2() != null) {
            int first = profile.getPayDay1();
            int second = profile.getPayDay2();
            return first <= second ? new int[] {first, second} : new int[] {second, first};
        }
        return new int[] {profile.getPayDay1()};
    }

    private int nextNumber(Long userId) {
        Integer max = records.findMaxRecordNumber(userId);
        return max == null ? 1 : max + 1;
    }

    private static void requireDay(Integer day) {
        if (day == null || day < 1 || day > 31) {
            throw new IllegalArgumentException("Send a day of the month from 1 to 31.");
        }
    }

    public record SavedOt(OtRecordEntity record, MonthSummary month) {
    }

    public record MonthSummary(YearMonth month, List<OtRecordEntity> records, BigDecimal paidHours, BigDecimal amount) {
    }

    public record Payday(LocalDate date, BigDecimal amount) {
    }

    public record UpcomingSalary(SalaryProfileEntity profile, BigDecimal hourly, List<Payday> next) {
    }
}
