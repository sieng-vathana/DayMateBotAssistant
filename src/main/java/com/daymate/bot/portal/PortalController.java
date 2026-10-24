package com.daymate.bot.portal;

import com.daymate.bot.domain.Language;
import com.daymate.bot.domain.OtShift;
import com.daymate.bot.domain.OtTimeRange;
import com.daymate.bot.entity.OtRecordEntity;
import com.daymate.bot.entity.PortalUserEntity;
import com.daymate.bot.entity.SalaryProfileEntity;
import com.daymate.bot.ot.MonthParser;
import com.daymate.bot.ot.OtRecordService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
public class PortalController {
    private final PortalAccountService accounts;
    private final OtRecordService overtime;

    public PortalController(PortalAccountService accounts, OtRecordService overtime) {
        this.accounts = accounts;
        this.overtime = overtime;
    }

    @PostMapping("/api/auth/register")
    public AuthResponse register(@RequestBody Credentials body) {
        String token = accounts.register(body.username(), body.password());
        return auth(token);
    }

    @PostMapping("/api/auth/login")
    public AuthResponse login(@RequestBody Credentials body) {
        String token = accounts.login(body.username(), body.password());
        return auth(token);
    }

    @PostMapping("/api/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            accounts.logout(header.substring(7).trim());
        }
    }

    @GetMapping("/api/portal/me")
    public AuthResponse me(HttpServletRequest request) {
        return profile(user(request), null);
    }

    @PostMapping("/api/portal/link")
    public AuthResponse link(HttpServletRequest request, @RequestBody LinkBody body) {
        return profile(accounts.link(user(request), body.code()), null);
    }

    @PutMapping("/api/portal/language")
    public AuthResponse language(HttpServletRequest request, @RequestBody LanguageBody body) {
        PortalUserEntity user = user(request);
        accounts.setLanguage(user, Language.from(body.language()));
        return profile(user, null);
    }

    @GetMapping("/api/portal/salary")
    public SalaryView salary(HttpServletRequest request) {
        return salaryView(overtime.salary(user(request).ownerId()).orElse(null));
    }

    @PutMapping("/api/portal/salary")
    public SalaryView saveSalary(HttpServletRequest request, @RequestBody SalaryBody body) {
        PortalUserEntity user = user(request);
        boolean acleda = Boolean.TRUE.equals(body.acleda());
        int times = acleda ? 2 : body.payTimes() == null ? 1 : body.payTimes();
        int day1 = acleda ? 12 : body.payDay1() == null ? 0 : body.payDay1();
        Integer day2 = acleda ? 26 : body.payDay2();
        int week = acleda ? 5 : body.workDaysPerWeek() == null ? 5 : body.workDaysPerWeek();
        SalaryProfileEntity saved = overtime.saveSalary(
                user.ownerId(), acleda, times, day1, day2, week, body.monthlySalary());
        return salaryView(saved);
    }

    @GetMapping("/api/portal/ot")
    public MonthView month(HttpServletRequest request, @RequestParam(required = false) String month) {
        return monthView(overtime.monthSummary(user(request).ownerId(), parseMonth(month)));
    }

    @PostMapping("/api/portal/ot")
    public MonthView create(HttpServletRequest request, @RequestBody OtBody body) {
        PortalUserEntity user = user(request);
        LocalDate date = parseDate(body.date());
        LocalTime start = parseTime(body.start());
        LocalTime end = parseTime(body.end());
        OtShift shift = OtShift.parse(body.shift()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose Holiday, Evening, or Weekend."));
        overtime.record(user.ownerId(), user.ownerId(), date, new OtTimeRange(start, end), shift);
        return monthView(overtime.monthSummary(user.ownerId(), YearMonth.from(date)));
    }

    @DeleteMapping("/api/portal/ot/{number}")
    public MonthView delete(HttpServletRequest request, @PathVariable int number,
                            @RequestParam(required = false) String month) {
        PortalUserEntity user = user(request);
        YearMonth target = parseMonth(month);
        overtime.deleteRecord(user.ownerId(), number, target);
        return monthView(overtime.monthSummary(user.ownerId(), target));
    }

    @GetMapping("/api/portal/history")
    public List<String> history(HttpServletRequest request) {
        return overtime.historyMonths(user(request).ownerId()).stream().map(YearMonth::toString).toList();
    }

    @GetMapping("/api/portal/upcoming")
    public UpcomingView upcoming(HttpServletRequest request) {
        OtRecordService.UpcomingSalary upcoming = overtime.upcoming(user(request).ownerId());
        return new UpcomingView(
                upcoming.hourly(),
                upcoming.profile().getMonthlySalary(),
                upcoming.next().stream()
                        .map(pay -> new PaydayView(pay.date().toString(), pay.amount()))
                        .toList());
    }

    private AuthResponse auth(String token) {
        PortalUserEntity user = accounts.userForToken(token);
        return profile(user, token);
    }

    private AuthResponse profile(PortalUserEntity user, String token) {
        return new AuthResponse(
                token,
                user.getUsername(),
                user.getTelegramUserId() != null,
                user.getLanguage());
    }

    private SalaryView salaryView(SalaryProfileEntity profile) {
        if (profile == null) {
            return new SalaryView(false, false, null, null, null, null, null);
        }
        return new SalaryView(
                true,
                profile.isAcledaEmployee(),
                profile.getPayTimes(),
                profile.getPayDay1(),
                profile.getPayDay2(),
                profile.resolvedWorkDaysPerWeek(),
                profile.getMonthlySalary());
    }

    private MonthView monthView(OtRecordService.MonthSummary summary) {
        return new MonthView(
                summary.month().toString(),
                summary.paidHours(),
                summary.amount(),
                summary.records().stream().map(this::row).toList());
    }

    private OtRow row(OtRecordEntity record) {
        return new OtRow(
                record.getRecordNumber(),
                record.getWorkDate().toString(),
                record.getStartTime().toString(),
                record.getEndTime().toString(),
                record.getShift().name(),
                record.getPaidHours(),
                record.getBreakHours(),
                record.getHourlyRate(),
                record.getAmount());
    }

    private static PortalUserEntity user(HttpServletRequest request) {
        return (PortalUserEntity) request.getAttribute(PortalAuthFilter.USER);
    }

    private YearMonth parseMonth(String raw) {
        if (raw == null || raw.isBlank()) {
            return overtime.currentMonth();
        }
        return MonthParser.parse(raw, overtime.today())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Month must look like 2026-10."));
    }

    private static LocalDate parseDate(String raw) {
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date must look like 2026-10-24.");
        }
    }

    private static LocalTime parseTime(String raw) {
        try {
            return LocalTime.parse(raw.length() == 5 ? raw : raw);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Time must look like 13:00.");
        }
    }

    public record Credentials(String username, String password) {
    }

    public record LinkBody(String code) {
    }

    public record LanguageBody(String language) {
    }

    public record SalaryBody(Boolean acleda, Integer payTimes, Integer payDay1, Integer payDay2,
                             Integer workDaysPerWeek, BigDecimal monthlySalary) {
    }

    public record OtBody(String date, String start, String end, String shift) {
    }

    public record AuthResponse(String token, String username, boolean linked, String language) {
    }

    public record SalaryView(boolean saved, boolean acleda, Integer payTimes, Integer payDay1, Integer payDay2,
                             Integer workDaysPerWeek, BigDecimal monthlySalary) {
    }

    public record OtRow(int number, String date, String start, String end, String shift,
                        BigDecimal paidHours, BigDecimal breakHours, BigDecimal hourlyRate, BigDecimal amount) {
    }

    public record MonthView(String month, BigDecimal paidHours, BigDecimal amount, List<OtRow> records) {
    }

    public record PaydayView(String date, BigDecimal amount) {
    }

    public record UpcomingView(BigDecimal hourly, BigDecimal monthlySalary, List<PaydayView> next) {
    }
}
