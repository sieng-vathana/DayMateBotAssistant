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
                                          BigDecimal monthlySalary) {
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
        SalaryProfileEntity profile = new SalaryProfileEntity(
                userId, acleda, payTimes, payDay1, payDay2, monthlySalary.setScale(2, java.math.RoundingMode.HALF_UP),
                Instant.now(clock));
        return salaries.save(profile);
    }

    @Transactional
    public SavedOt record(Long userId, Long chatId, OtTimeRange range) {
        SalaryProfileEntity profile = salaries.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Set your base salary before recording OT."));
        LocalDate today = LocalDate.now(clock);
        OtCalculator.Quote quote = OtCalculator.quote(profile.getMonthlySalary(), today, range.start(), range.end());
        int number = nextNumber(userId);
        OtRecordEntity saved = records.save(new OtRecordEntity(
                userId, chatId, number, today, range.start(), range.end(), quote.shift(),
                quote.grossHours(), quote.breakHours(), quote.paidHours(), quote.hourlyRate(), quote.amount(),
                Instant.now(clock)));
        MonthSummary month = monthSummary(userId, YearMonth.from(today));
        return new SavedOt(saved, month);
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

    public ZoneId zoneId() {
        return zoneId;
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
}
