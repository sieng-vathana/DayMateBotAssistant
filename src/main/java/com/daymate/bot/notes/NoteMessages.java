package com.daymate.bot.notes;

import com.daymate.bot.i18n.Texts;

public final class NoteMessages {
    private NoteMessages() {
    }

    public static String gone() {
        return Texts.t("That note is no longer available.", "កំណត់ចំណាំនោះមិនមានទៀតទេ។");
    }

    public static String needText() {
        return Texts.t("Send the note text.", "ផ្ញើអត្ថបទកំណត់ចំណាំ។");
    }

    public static String shorter() {
        return Texts.t("Send a shorter note.", "ផ្ញើកំណត់ចំណាំខ្លីជាងនេះ។");
    }

    public static String searchLength() {
        return Texts.t("Send a search of 2 to 40 characters.", "ផ្ញើពាក្យស្វែងរកពី ២ ទៅ ៤០ តួ។");
    }

    public static String anotherSearch() {
        return Texts.t(
                "Send /search to look for something else.",
                "ផ្ញើ /search ដើម្បីស្វែងរកអ្វីផ្សេង។");
    }

    public static String searchAgain() {
        return Texts.t(
                "Send /search and a word to look again.",
                "ផ្ញើ /search និងពាក្យមួយដើម្បីស្វែងរកម្ដងទៀត។");
    }

    public static String askSearch() {
        return Texts.t("Send a word or a #tag.", "ផ្ញើពាក្យ ឬ #ស្លាក។");
    }

    public static String askDate() {
        return Texts.t(
                "Send the date like 25 Oct 2026 or 2026-10-25.",
                "ផ្ញើកាលបរិច្ឆេទដូច 25 Oct 2026 ឬ 2026-10-25។");
    }

    public static String askTime() {
        return Texts.t("Send the time like 20:00 or 8:00 PM.", "ផ្ញើម៉ោងដូច 20:00 ឬ 8:00 PM។");
    }

    public static String past() {
        return Texts.t("That time has already passed.", "ម៉ោងនោះបានកន្លងផុតហើយ។");
    }

    public static String noReminder() {
        return Texts.t("There is no reminder on this note.", "កំណត់ចំណាំនេះមិនមានការរំលឹកទេ។");
    }

    public static String buttonGone() {
        return Texts.t("This button is no longer available.", "ប៊ូតុងនេះមិនអាចប្រើបានទៀតទេ។");
    }
}
