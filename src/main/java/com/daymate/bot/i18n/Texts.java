package com.daymate.bot.i18n;

import com.daymate.bot.domain.Language;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class Texts {
    private static final DateTimeFormatter EN_DAY = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter EN_MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter EN_MONTH_LONG = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final String[] KM_WEEKDAY = {
            "ចន្ទ", "អង្គារ", "ពុធ", "ព្រហស្បតិ៍", "សុក្រ", "សៅរ៍", "អាទិត្យ"
    };
    private static final String[] KM_MONTH = {
            "មករា", "កុម្ភៈ", "មីនា", "មេសា", "ឧសភា", "មិថុនា",
            "កក្កដា", "សីហា", "កញ្ញា", "តុលា", "វិច្ឆិកា", "ធ្នូ"
    };

    private Texts() {
    }

    public static String t(String english, String khmer) {
        return LanguageContext.get() == Language.KM ? khmer : english;
    }

    public static String day(LocalDate date) {
        if (LanguageContext.get() != Language.KM) {
            return EN_DAY.format(date);
        }
        return KM_WEEKDAY[date.getDayOfWeek().getValue() - 1] + ", "
                + date.getDayOfMonth() + " " + KM_MONTH[date.getMonthValue() - 1] + " " + date.getYear();
    }

    public static String month(YearMonth month) {
        if (LanguageContext.get() != Language.KM) {
            return EN_MONTH.format(month);
        }
        return KM_MONTH[month.getMonthValue() - 1] + " " + month.getYear();
    }

    public static String monthLong(LocalDate date) {
        if (LanguageContext.get() != Language.KM) {
            return EN_MONTH_LONG.format(date);
        }
        return KM_MONTH[date.getMonthValue() - 1] + " " + date.getYear();
    }
}
