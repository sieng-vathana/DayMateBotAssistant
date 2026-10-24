package com.daymate.bot.portal;

import com.daymate.bot.domain.Language;
import com.daymate.bot.entity.LinkCodeEntity;
import com.daymate.bot.entity.PortalSessionEntity;
import com.daymate.bot.entity.PortalUserEntity;
import com.daymate.bot.entity.SalaryProfileEntity;
import com.daymate.bot.entity.UserSettingsEntity;
import com.daymate.bot.repository.LinkCodeRepository;
import com.daymate.bot.repository.OtRecordRepository;
import com.daymate.bot.repository.PortalSessionRepository;
import com.daymate.bot.repository.PortalUserRepository;
import com.daymate.bot.repository.SalaryProfileRepository;
import com.daymate.bot.repository.UserSettingsRepository;
import com.daymate.bot.service.LanguageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class PortalAccountService {
    private static final Duration SESSION = Duration.ofDays(30);
    private static final Duration CODE = Duration.ofMinutes(15);
    private final PortalUserRepository users;
    private final PortalSessionRepository sessions;
    private final LinkCodeRepository codes;
    private final OtRecordRepository records;
    private final SalaryProfileRepository salaries;
    private final UserSettingsRepository settings;
    private final LanguageService languages;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final org.springframework.security.crypto.password.PasswordEncoder passwords =
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();

    @Value("${portal.public-url:http://13.212.32.202:8001}")
    private String portalUrl;

    public PortalAccountService(PortalUserRepository users, PortalSessionRepository sessions, LinkCodeRepository codes,
                                OtRecordRepository records, SalaryProfileRepository salaries,
                                UserSettingsRepository settings, LanguageService languages, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.codes = codes;
        this.records = records;
        this.salaries = salaries;
        this.settings = settings;
        this.languages = languages;
        this.clock = clock;
    }

    @Transactional
    public String register(String username, String password) {
        String name = normalize(username);
        if (!name.matches("[a-z0-9_]{3,32}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Username must be 3–32 letters, numbers, or underscores.");
        }
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be 8–72 characters.");
        }
        if (users.findByUsername(name).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is already taken.");
        }
        PortalUserEntity user = users.save(new PortalUserEntity(
                name, passwords.encode(password), Language.EN.name(), Instant.now(clock)));
        return openSession(user);
    }

    @Transactional
    public String login(String username, String password) {
        PortalUserEntity user = users.findByUsername(normalize(username)).orElse(null);
        if (user == null || password == null || !passwords.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Username or password is wrong.");
        }
        return openSession(user);
    }

    @Transactional
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            sessions.deleteById(hash(token));
        }
    }

    @Transactional
    public PortalUserEntity userForToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        PortalSessionEntity session = sessions.findById(hash(token)).orElse(null);
        if (session == null || session.getExpiresAt().isBefore(Instant.now(clock))) {
            if (session != null) {
                sessions.delete(session);
            }
            return null;
        }
        return users.findById(session.getUserId()).orElse(null);
    }

    @Transactional
    public String issueLinkCode(Long telegramUserId) {
        codes.deleteByTelegramUserId(telegramUserId);
        String code = randomCode();
        codes.save(new LinkCodeEntity(code, telegramUserId, Instant.now(clock).plus(CODE)));
        return code;
    }

    public String portalUrl() {
        return portalUrl;
    }

    @Transactional
    public PortalUserEntity link(PortalUserEntity user, String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase();
        LinkCodeEntity link = codes.findById(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "That link code is not valid."));
        if (link.getExpiresAt().isBefore(Instant.now(clock))) {
            codes.delete(link);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That link code has expired. Send /link again.");
        }
        users.findByTelegramUserId(link.getTelegramUserId()).ifPresent(other -> {
            if (!other.getId().equals(user.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "That Telegram account is already linked.");
            }
        });
        long from = user.ownerId();
        long to = link.getTelegramUserId();
        if (from != to) {
            records.moveOwner(from, to, to);
            moveSalary(from, to);
            moveLanguage(from, to, user.getLanguage());
        }
        user.setTelegramUserId(to);
        codes.delete(link);
        return users.save(user);
    }

    @Transactional
    public void setLanguage(PortalUserEntity user, Language language) {
        user.setLanguage(language.name());
        users.save(user);
        if (user.getTelegramUserId() != null) {
            languages.set(user.getTelegramUserId(), language);
        }
    }

    private void moveSalary(long from, long to) {
        SalaryProfileEntity source = salaries.findById(from).orElse(null);
        if (source == null) {
            return;
        }
        if (salaries.findById(to).isEmpty()) {
            salaries.save(new SalaryProfileEntity(
                    to, source.isAcledaEmployee(), source.getPayTimes(), source.getPayDay1(), source.getPayDay2(),
                    source.resolvedWorkDaysPerWeek(), source.getMonthlySalary(), source.getUpdatedAt()));
        }
        salaries.delete(source);
    }

    private void moveLanguage(long from, long to, String portalLanguage) {
        if (settings.findById(to).isEmpty()) {
            String language = settings.findById(from).map(UserSettingsEntity::getLanguage).orElse(portalLanguage);
            settings.save(new UserSettingsEntity(to, language, Instant.now(clock)));
        }
        if (from != to) {
            settings.deleteById(from);
        }
    }

    private String openSession(PortalUserEntity user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        sessions.save(new PortalSessionEntity(hash(token), user.getId(), Instant.now(clock).plus(SESSION)));
        return token;
    }

    private String randomCode() {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder code = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            code.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return code.toString();
    }

    private static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
