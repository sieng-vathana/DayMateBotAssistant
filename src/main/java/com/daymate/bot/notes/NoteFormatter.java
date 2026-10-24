package com.daymate.bot.notes;

import com.daymate.bot.entity.NoteEntity;
import com.daymate.bot.entity.NoteReminderEntity;
import com.daymate.bot.entity.NoteTagEntity;
import com.daymate.bot.i18n.Texts;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Component
public class NoteFormatter {
    /** This phone wraps a text bubble past about 60 columns. */
    private static final int LIST_WIDTH = 60;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final ZoneId zone;

    public NoteFormatter(ZoneId zone) {
        this.zone = zone;
    }

    public String menu() {
        return Texts.t("📌 My Notes", "📌 កំណត់ចំណាំរបស់ខ្ញុំ")
                + "\n\n"
                + Texts.t("Save anything you want to remember.", "រក្សាទុកអ្វីដែលអ្នកចង់ចាំ។")
                + "\n"
                + Texts.t(
                "Keep your ideas, important information, links, and reminders in one place.",
                "រក្សាគំនិត ព័ត៌មានសំខាន់ តំណ និងការរំលឹកទុកក្នុងកន្លែងតែមួយ។");
    }

    public String saved(NoteEntity note) {
        return Texts.t("✅ Note Saved!", "✅ បានរក្សាទុក!")
                + "\n\n"
                + note.getContent()
                + "\n\n"
                + Texts.t("📅 Saved: ", "📅 រក្សាទុក៖ ")
                + formatDate(note.getCreatedAt());
    }

    public String myNotes(int pageNumber, long total, List<NoteEntity> notes) {
        return page(Texts.t("📋 My Notes", "📋 កំណត់ចំណាំរបស់ខ្ញុំ"), countNotes(total), pageNumber, notes);
    }

    public String pinned(int pageNumber, long total, List<NoteEntity> notes) {
        return page(Texts.t("📌 Pinned Notes", "📌 កំណត់ចំណាំដែលខ្ទាស់"), countPinned(total), pageNumber, notes);
    }

    public String searchResults(String keyword, int pageNumber, long total, List<NoteEntity> notes) {
        String heading = Texts.t("🔍 Search: ", "🔍 ស្វែងរក៖ ") + keyword;
        return page(heading, countFound(total), pageNumber, notes);
    }

    public String tagResults(String tagName, int pageNumber, long total, List<NoteEntity> notes) {
        return page("🔍 #" + tagName, countFound(total), pageNumber, notes);
    }

    public String detail(NoteEntity note, List<NoteTagEntity> tags, NoteReminderEntity reminder) {
        StringBuilder text = new StringBuilder();
        text.append(Texts.t("📌 Note #", "📌 កំណត់ចំណាំ #")).append(note.getId());
        text.append("\n\n").append(note.getTitle());
        if (note.getContent() != null && !note.getContent().equals(note.getTitle())) {
            text.append("\n\n").append(note.getContent());
        }
        text.append("\n\n").append(Texts.t("📅 ", "📅 ")).append(formatDate(note.getCreatedAt()));
        if (tags != null && !tags.isEmpty()) {
            text.append("\n");
            for (int i = 0; i < tags.size(); i++) {
                if (i > 0) {
                    text.append(' ');
                }
                text.append('#').append(tags.get(i).getTagName());
            }
        }
        if (reminder != null && NoteReminderEntity.PENDING.equals(reminder.getStatus())) {
            text.append("\n")
                    .append(Texts.t("⏰ Reminder: ", "⏰ ការរំលឹក៖ "))
                    .append(formatDate(reminder.getRemindAt()))
                    .append(", ")
                    .append(formatTime(reminder.getRemindAt()));
        }
        return text.toString();
    }

    public String deleteConfirm(NoteEntity note) {
        return Texts.t("Delete this note?", "លុបកំណត់ចំណាំនេះ?") + "\n\n" + note.getTitle();
    }

    public String deleted() {
        return Texts.t("🗑 Deleted.", "🗑 បានលុប។");
    }

    public String editPrompt() {
        return Texts.t("✏️ Edit Note", "✏️ កែកំណត់ចំណាំ")
                + "\n\n"
                + Texts.t("Send the new text.", "ផ្ញើអត្ថបទថ្មី។");
    }

    public String askWhen() {
        return Texts.t("⏰ When should I remind you?", "⏰ តើខ្ញុំគួររំលឹកអ្នកនៅពេលណា?");
    }

    public String scheduled(NoteEntity note, Instant remindAt) {
        return Texts.t("⏰ Reminder Scheduled", "⏰ បានកំណត់ការរំលឹក")
                + "\n\n"
                + note.getTitle()
                + "\n"
                + formatDate(remindAt)
                + "\n🕗 "
                + formatTime(remindAt);
    }

    public String fired(NoteEntity note) {
        StringBuilder text = new StringBuilder();
        text.append(Texts.t("⏰ Reminder", "⏰ ការរំលឹក"));
        text.append("\n\n").append(note.getTitle());
        if (note.getContent() != null && !note.getContent().equals(note.getTitle())) {
            text.append("\n\n").append(note.getContent());
        }
        return text.toString();
    }

    public String formatDate(Instant instant) {
        if (instant == null) {
            return "";
        }
        return DATE.format(instant.atZone(zone));
    }

    public String formatTime(Instant instant) {
        if (instant == null) {
            return "";
        }
        ZonedDateTime zoned = instant.atZone(zone);
        int hour = zoned.getHour();
        int hour12 = hour % 12;
        if (hour12 == 0) {
            hour12 = 12;
        }
        String suffix = hour < 12 ? "AM" : "PM";
        return hour12 + ":" + String.format(Locale.ENGLISH, "%02d", zoned.getMinute()) + " " + suffix;
    }

    private String page(String heading, String count, int pageNumber, List<NoteEntity> notes) {
        StringBuilder text = new StringBuilder();
        text.append(heading).append("\n\n").append(count);
        int index = 0;
        for (NoteEntity note : notes) {
            int number = pageNumber * NoteService.PAGE_SIZE + index + 1;
            text.append("\n").append(listLine(number, note));
            index++;
        }
        return text.toString();
    }

    private String listLine(int number, NoteEntity note) {
        String date = formatDate(note.getCreatedAt());
        String prefix = number + ". " + (note.isPinned() ? "📌 " : "📝 ");
        int titleRoom = LIST_WIDTH - displayWidth(prefix) - displayWidth(date) - 1;
        if (titleRoom < 1) {
            titleRoom = 1;
        }
        String title = cutToWidth(note.getTitle(), titleRoom);
        String body = prefix + title;
        int spaces = LIST_WIDTH - displayWidth(body) - displayWidth(date);
        if (spaces < 1) {
            spaces = 1;
        }
        return body + " ".repeat(spaces) + date;
    }

    private static String cutToWidth(String value, int maxWidth) {
        String text = value == null ? "" : value.strip();
        if (displayWidth(text) <= maxWidth) {
            return text;
        }
        StringBuilder kept = new StringBuilder();
        int width = 0;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            int widthOfPoint = columnWidth(codePoint);
            if (width + widthOfPoint > maxWidth) {
                break;
            }
            kept.appendCodePoint(codePoint);
            width += widthOfPoint;
            offset += Character.charCount(codePoint);
        }
        return kept.toString();
    }

    private static int displayWidth(String value) {
        int width = 0;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            width += columnWidth(codePoint);
            offset += Character.charCount(codePoint);
        }
        return width;
    }

    private static int columnWidth(int codePoint) {
        if (codePoint <= 0x007E) {
            return 1;
        }
        return 2;
    }

    private static String countNotes(long total) {
        if (total == 0) {
            return Texts.t("You have no saved notes yet.", "អ្នកមិនទាន់មានកំណត់ចំណាំទេ។");
        }
        if (total == 1) {
            return Texts.t("You have 1 saved note.", "អ្នកមានកំណត់ចំណាំ ១។");
        }
        return Texts.t("You have " + total + " saved notes.", "អ្នកមានកំណត់ចំណាំ " + total + "។");
    }

    private static String countPinned(long total) {
        if (total == 0) {
            return Texts.t("You have no pinned notes.", "អ្នកមិនមានកំណត់ចំណាំដែលខ្ទាស់ទេ។");
        }
        if (total == 1) {
            return Texts.t("You have 1 pinned note.", "អ្នកមានកំណត់ចំណាំដែលខ្ទាស់ ១។");
        }
        return Texts.t("You have " + total + " pinned notes.", "អ្នកមានកំណត់ចំណាំដែលខ្ទាស់ " + total + "។");
    }

    private static String countFound(long total) {
        if (total == 0) {
            return Texts.t("No matching notes.", "រកមិនឃើញកំណត់ចំណាំទេ។");
        }
        if (total == 1) {
            return Texts.t("Found 1 note.", "រកឃើញកំណត់ចំណាំ ១។");
        }
        return Texts.t("Found " + total + " notes.", "រកឃើញកំណត់ចំណាំ " + total + "។");
    }

    public static String preview(String title, int max) {
        String value = title == null ? "" : title.strip();
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
