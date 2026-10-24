package com.daymate.bot.modules.notes;

import com.daymate.bot.core.access.AccessDeniedException;
import com.daymate.bot.core.access.AccessMessages;
import com.daymate.bot.core.module.BotContext;
import com.daymate.bot.core.module.BotModule;
import com.daymate.bot.core.module.FeatureDeclaration;
import com.daymate.bot.core.module.MenuAction;
import com.daymate.bot.domain.ConversationStep;
import com.daymate.bot.entity.ConversationStateEntity;
import com.daymate.bot.entity.NoteEntity;
import com.daymate.bot.entity.NoteReminderEntity;
import com.daymate.bot.entity.NoteTagEntity;
import com.daymate.bot.i18n.Texts;
import com.daymate.bot.notes.NoteFormatter;
import com.daymate.bot.notes.NoteMessages;
import com.daymate.bot.notes.NoteService;
import com.daymate.bot.notes.NoteText;
import com.daymate.bot.notes.NoteTimes;
import com.daymate.bot.service.ConversationLookup;
import com.daymate.bot.service.ConversationService;
import com.daymate.bot.telegram.TelegramApiClient;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

@Component
public class NotesModule implements BotModule {
    public static final String ID = "notes";
    public static final String VIEW = "notes.view";
    public static final String CREATE = "notes.create";
    public static final String EDIT = "notes.edit";
    public static final String DELETE = "notes.delete";
    public static final String SEARCH = "notes.search";
    public static final String PIN = "notes.pin";
    public static final String REMINDER_CREATE = "notes.reminder.create";
    public static final String REMINDER_CANCEL = "notes.reminder.cancel";

    private final NoteService notes;
    private final ConversationService conversations;
    private final TelegramApiClient telegram;
    private final NoteFormatter formatter;
    private final NoteTimes times;

    public NotesModule(NoteService notes, ConversationService conversations, TelegramApiClient telegram,
                       NoteFormatter formatter, NoteTimes times) {
        this.notes = notes;
        this.conversations = conversations;
        this.telegram = telegram;
        this.formatter = formatter;
        this.times = times;
    }

    @Override
    public String moduleId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "📌 Notes";
    }

    @Override
    public String description() {
        return "Save notes and reminders.";
    }

    @Override
    public int displayOrder() {
        return 40;
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    @Override
    public List<String> commands() {
        return List.of("/note", "/notes", "/my-notes", "/mynotes", "/my_notes", "/search", "/pinned");
    }

    @Override
    public List<String> callbackPrefixes() {
        return List.of("note");
    }

    @Override
    public List<FeatureDeclaration> features() {
        return List.of(
                open(VIEW), open(CREATE), open(EDIT), open(DELETE),
                open(SEARCH), open(PIN), open(REMINDER_CREATE), open(REMINDER_CANCEL));
    }

    @Override
    public List<MenuAction> menuActions() {
        return List.of(
                new MenuAction("new", "➕ New Note", "➕ កំណត់ចំណាំថ្មី", CREATE),
                new MenuAction("list", "📋 My Notes", "📋 កំណត់ចំណាំរបស់ខ្ញុំ", VIEW),
                new MenuAction("search", "🔍 Search Notes", "🔍 ស្វែងរក", SEARCH),
                new MenuAction("pinned", "📌 Pinned Notes", "📌 កំណត់ចំណាំដែលខ្ទាស់", PIN));
    }

    public List<List<Map<String, String>>> optionRows(Predicate<String> allowed) {
        List<List<Map<String, String>>> rows = new ArrayList<>();
        for (MenuAction action : menuActions()) {
            if (allowed.test(action.featureKey())) {
                rows.add(List.of(button(action.label(), menuCallback(action))));
            }
        }
        return rows;
    }

    @Override
    public String featureForCommand(String command) {
        return switch (normalize(command)) {
            case "/note" -> CREATE;
            case "/notes", "/my-notes", "/mynotes", "/my_notes" -> VIEW;
            case "/search" -> SEARCH;
            case "/pinned" -> PIN;
            default -> null;
        };
    }

    @Override
    public String featureForCallback(String data) {
        if (data == null || data.equals("note:cancel")) {
            return null;
        }
        // Longer callback names share a prefix with a different feature, so they are matched first.
        if (data.startsWith("note:delok:") || data.startsWith("note:del:")) {
            return DELETE;
        }
        if (data.startsWith("note:pinlist")) {
            return PIN;
        }
        if (data.startsWith("note:unpin:") || data.startsWith("note:pin:")) {
            return PIN;
        }
        if (data.startsWith("note:remclr:") || data.equals("note:remclr")) {
            return REMINDER_CANCEL;
        }
        if (data.startsWith("note:tags")) {
            return SEARCH;
        }
        if (data.startsWith("note:tag:") || data.equals("note:search") || data.startsWith("note:q:")) {
            return SEARCH;
        }
        if (data.startsWith("note:edit:")) {
            return EDIT;
        }
        if (data.equals("note:new")) {
            return CREATE;
        }
        if (data.equals("note:menu") || data.startsWith("note:list:") || data.startsWith("note:open:")) {
            return VIEW;
        }
        if (data.startsWith("note:rem")) {
            return REMINDER_CREATE;
        }
        return VIEW;
    }

    @Override
    public String featureForState(ConversationStateEntity state) {
        if (state == null || state.getStep() == null) {
            return VIEW;
        }
        return switch (state.getStep()) {
            case NOTES_WAITING_CONTENT -> CREATE;
            case NOTES_WAITING_EDIT_CONTENT -> EDIT;
            case NOTES_WAITING_SEARCH_QUERY -> SEARCH;
            case NOTES_WAITING_REMINDER_DATE, NOTES_WAITING_REMINDER_TIME -> REMINDER_CREATE;
            default -> VIEW;
        };
    }

    @Override
    public String commandForMenuAction(String action) {
        return switch (action) {
            case "new" -> "/note";
            case "list" -> "/my-notes";
            case "search" -> "/search";
            case "pinned" -> "/pinned";
            default -> null;
        };
    }

    @Override
    public void showMenu(BotContext context) {
        renderMenu(context);
    }

    @Override
    public void handleCommand(BotContext context, String command, String arguments) {
        try {
            switch (normalize(command)) {
                case "/notes" -> renderMenu(context);
                case "/my-notes", "/mynotes", "/my_notes" -> showList(context, 0);
                case "/note" -> {
                    if (arguments == null || arguments.isBlank()) {
                        startContent(context);
                    } else {
                        quickSave(context, arguments);
                    }
                }
                case "/search" -> {
                    if (arguments == null || arguments.isBlank()) {
                        askSearch(context);
                    } else {
                        showSearch(context, arguments, 0);
                    }
                }
                case "/pinned" -> showPinned(context, 0);
                default -> telegram.sendMessage(context.chatId(), NoteMessages.buttonGone());
            }
        } catch (AccessDeniedException exception) {
            telegram.sendMessage(context.chatId(), AccessMessages.actionUnavailable());
        } catch (IllegalArgumentException exception) {
            telegram.sendMessage(context.chatId(), exception.getMessage());
        }
    }

    @Override
    public boolean handleCallback(BotContext context, String data) {
        if (data == null || !(data.equals("note") || data.startsWith("note:"))) {
            return false;
        }
        try {
            return routeCallback(context, data);
        } catch (AccessDeniedException exception) {
            telegram.answerCallback(context.callbackId(), AccessMessages.actionUnavailable(), true);
            return true;
        } catch (IllegalArgumentException exception) {
            telegram.answerCallback(context.callbackId(), exception.getMessage(), true);
            return true;
        }
    }

    @Override
    public void handleMessage(BotContext context, String message) {
        try {
            ConversationStateEntity state = context.state();
            if (state == null || state.getUserId() == null || !state.getUserId().equals(context.userId())) {
                return;
            }
            switch (state.getStep()) {
                case NOTES_WAITING_CONTENT -> saveContent(context, message);
                case NOTES_WAITING_EDIT_CONTENT -> saveEdit(context, message);
                case NOTES_WAITING_SEARCH_QUERY -> searchText(context, state, message);
                case NOTES_WAITING_REMINDER_DATE -> dateText(context, state, message);
                case NOTES_WAITING_REMINDER_TIME -> timeText(context, state, message);
                default -> renderMenu(context);
            }
        } catch (AccessDeniedException exception) {
            telegram.sendMessage(context.chatId(), AccessMessages.actionUnavailable());
        } catch (IllegalArgumentException exception) {
            telegram.sendMessage(context.chatId(), exception.getMessage());
        }
    }

    private boolean routeCallback(BotContext context, String data) {
        if ("note:cancel".equals(data)) {
            return cancelToMenu(context);
        }
        String[] parts = data.split(":", -1);
        String action = parts.length > 1 ? parts[1] : "";
        return switch (action) {
            case "new" -> {
                answer(context, "Notes");
                startContent(context);
                yield true;
            }
            case "menu" -> {
                answer(context, "Notes");
                renderMenu(context);
                yield true;
            }
            case "search" -> {
                answer(context, "Search");
                askSearch(context);
                yield true;
            }
            case "list" -> openPage(context, "Notes", pageAt(parts, 2), this::showList);
            case "pinlist" -> openPage(context, "Pin", pageAt(parts, 2), this::showPinned);
            case "q" -> openSearchPage(context, pageAt(parts, 2));
            case "open" -> openNote(context, longAt(parts, 2), partAt(parts, 3));
            case "edit" -> startEdit(context, longAt(parts, 2));
            case "pin" -> changePin(context, longAt(parts, 2), true, partAt(parts, 3));
            case "unpin" -> changePin(context, longAt(parts, 2), false, partAt(parts, 3));
            case "del" -> confirmDelete(context, longAt(parts, 2), partAt(parts, 3));
            case "delok" -> deleteNote(context, longAt(parts, 2));
            case "tag" -> openTag(context, longAt(parts, 2), pageAt(parts, 3));
            case "rem" -> remindMenu(context, longAt(parts, 2), partAt(parts, 3));
            case "remtoday" -> remindPreset(context, longAt(parts, 2), times.todayAtEight());
            case "remtom" -> remindPreset(context, longAt(parts, 2), times.tomorrowAtEight());
            case "remdate" -> askDate(context, longAt(parts, 2), partAt(parts, 3));
            case "remclr" -> clearReminder(context, longAt(parts, 2), partAt(parts, 3));
            default -> unavailable(context);
        };
    }

    private void renderMenu(BotContext context) {
        clearOwn(context);
        List<List<Map<String, String>>> rows = new ArrayList<>(optionRows(context::allows));
        rows.add(row("⬅️ Main Menu", "⬅️ ម៉ឺនុយដើម", "menu:home"));
        present(context, formatter.menu(), rows);
    }

    private boolean cancelToMenu(BotContext context) {
        answer(context, "Cancelled");
        renderMenu(context);
        return true;
    }

    private void startContent(BotContext context) {
        conversations.save(fresh(context, ConversationStep.NOTES_WAITING_CONTENT, "wait"));
        present(context, NoteMessages.needText(), cancelKeyboard());
    }

    private void askSearch(BotContext context) {
        conversations.save(fresh(context, ConversationStep.NOTES_WAITING_SEARCH_QUERY, "wait"));
        present(context, NoteMessages.askSearch(), cancelKeyboard());
    }

    private void quickSave(BotContext context, String text) {
        NoteEntity note = notes.create(context.userId(), context.chatId(), text);
        conversations.clear(context.chatId());
        telegram.sendMessage(context.chatId(), formatter.saved(note), viewKeyboard(note.getId()));
    }

    private void saveContent(BotContext context, String message) {
        quickSave(context, message);
    }

    private void saveEdit(BotContext context, String message) {
        Long id = pendingId(context.state());
        if (id == null) {
            conversations.clear(context.chatId());
            throw new IllegalArgumentException(NoteMessages.gone());
        }
        notes.update(context.userId(), id, message);
        conversations.clear(context.chatId());
        renderDetail(context, id, "l0");
    }

    private void searchText(BotContext context, ConversationStateEntity state, String message) {
        if ("page".equals(state.getAction())) {
            telegram.sendMessage(context.chatId(), NoteMessages.anotherSearch());
            return;
        }
        showSearch(context, message, 0);
    }

    private void dateText(BotContext context, ConversationStateEntity state, String message) {
        Long id = pendingId(state);
        if (id == null) {
            conversations.clear(context.chatId());
            throw new IllegalArgumentException(NoteMessages.gone());
        }
        Optional<LocalDateTime> dateTime = NoteTimes.parseDateTime(message);
        if (dateTime.isPresent()) {
            scheduleAt(context, id, times.toInstant(dateTime.get()));
            return;
        }
        Optional<LocalDate> date = NoteTimes.parseDate(message);
        if (date.isEmpty()) {
            telegram.sendMessage(context.chatId(), NoteMessages.askDate());
            return;
        }
        state.setStep(ConversationStep.NOTES_WAITING_REMINDER_TIME);
        state.setModuleKey(ID);
        state.setAction("time");
        state.setPendingRange(Long.toString(id));
        state.setTitle(date.get().toString());
        conversations.save(state);
        telegram.sendMessage(context.chatId(), NoteMessages.askTime());
    }

    private void timeText(BotContext context, ConversationStateEntity state, String message) {
        Long id = pendingId(state);
        if (id == null) {
            conversations.clear(context.chatId());
            throw new IllegalArgumentException(NoteMessages.gone());
        }
        Optional<LocalDateTime> dateTime = NoteTimes.parseDateTime(message);
        if (dateTime.isPresent()) {
            scheduleAt(context, id, times.toInstant(dateTime.get()));
            return;
        }
        Optional<LocalTime> time = NoteTimes.parseTime(message);
        if (time.isEmpty()) {
            telegram.sendMessage(context.chatId(), NoteMessages.askTime());
            return;
        }
        LocalDate date;
        try {
            date = LocalDate.parse(state.getTitle());
        } catch (DateTimeParseException | NullPointerException exception) {
            telegram.sendMessage(context.chatId(), NoteMessages.askDate());
            return;
        }
        scheduleAt(context, id, times.toInstant(date.atTime(time.get())));
    }

    private boolean openPage(BotContext context, String toast, Integer page, PageAction action) {
        if (page == null) {
            return unavailable(context);
        }
        answer(context, toast);
        runAnswered(context, () -> action.show(context, page));
        return true;
    }

    private void showList(BotContext context, int page) {
        clearOwn(context);
        Page<NoteEntity> result = notes.list(context.userId(), page);
        showCollection(context, formatter.myNotes(result.getNumber(), result.getTotalElements(), result.getContent()),
                result, "l", "note:list:");
    }

    private void showPinned(BotContext context, int page) {
        clearOwn(context);
        Page<NoteEntity> result = notes.pinned(context.userId(), page);
        showCollection(context, formatter.pinned(result.getNumber(), result.getTotalElements(), result.getContent()),
                result, "p", "note:pinlist:");
    }

    private void showSearch(BotContext context, String keyword, int page) {
        String cleaned = NoteText.keyword(keyword);
        Page<NoteEntity> result = notes.search(context.userId(), cleaned, page);
        rememberSearch(context, cleaned);
        showCollection(context,
                formatter.searchResults(cleaned, result.getNumber(), result.getTotalElements(), result.getContent()),
                result, "q", "note:q:");
    }

    private boolean openSearchPage(BotContext context, Integer page) {
        if (page == null) {
            return unavailable(context);
        }
        answer(context, "Search");
        ConversationLookup lookup = conversations.read(context.chatId());
        if (!searchReady(context, lookup)) {
            present(context, NoteMessages.searchAgain(), cancelKeyboard());
            return true;
        }
        runAnswered(context, () -> showSearch(context, lookup.state().getTitle(), page));
        return true;
    }

    private boolean openTag(BotContext context, Long tagId, Integer page) {
        if (tagId == null || page == null) {
            return unavailable(context);
        }
        answer(context, "Search");
        runAnswered(context, () -> {
            clearOwn(context);
            String name = notes.ownedTagName(context.userId(), tagId);
            Page<NoteEntity> result = notes.byTag(context.userId(), tagId, page);
            showCollection(context,
                    formatter.tagResults(name, result.getNumber(), result.getTotalElements(), result.getContent()),
                    result, "g" + tagId + "_", "note:tag:" + tagId + ":");
        });
        return true;
    }

    private boolean openNote(BotContext context, Long id, String origin) {
        if (id == null) {
            return unavailable(context);
        }
        answer(context, "Notes");
        runAnswered(context, () -> {
            retainSearch(context);
            renderDetail(context, id, origin);
        });
        return true;
    }

    private boolean startEdit(BotContext context, Long id) {
        if (id == null) {
            return unavailable(context);
        }
        answer(context, "Edit");
        runAnswered(context, () -> {
            notes.get(context.userId(), id);
            ConversationStateEntity state = fresh(context, ConversationStep.NOTES_WAITING_EDIT_CONTENT, "edit");
            state.setPendingRange(Long.toString(id));
            conversations.save(state);
            present(context, formatter.editPrompt(), cancelKeyboard());
        });
        return true;
    }

    private boolean changePin(BotContext context, Long id, boolean pinned, String origin) {
        if (id == null) {
            return unavailable(context);
        }
        answer(context, "Pin");
        runAnswered(context, () -> {
            notes.setPinned(context.userId(), id, pinned);
            retainSearch(context);
            renderDetail(context, id, origin);
        });
        return true;
    }

    private boolean confirmDelete(BotContext context, Long id, String origin) {
        if (id == null) {
            return unavailable(context);
        }
        answer(context, "Delete");
        runAnswered(context, () -> {
            NoteEntity note = notes.get(context.userId(), id);
            List<List<Map<String, String>>> rows = List.of(
                    List.of(button(Texts.t("✅ Yes, Delete", "✅ បាទ/ចាស លុប"), "note:delok:" + id)),
                    List.of(button(Texts.t("Cancel", "បោះបង់"), openData(id, origin))));
            present(context, formatter.deleteConfirm(note), rows);
        });
        return true;
    }

    private boolean deleteNote(BotContext context, Long id) {
        if (id == null) {
            return unavailable(context);
        }
        answer(context, "Delete");
        runAnswered(context, () -> {
            notes.softDelete(context.userId(), id);
            clearOwn(context);
            present(context, formatter.deleted(),
                    List.of(row("⬅️ Notes Menu", "⬅️ ម៉ឺនុយកំណត់ចំណាំ", "note:menu")));
        });
        return true;
    }

    private boolean remindMenu(BotContext context, Long id, String origin) {
        if (id == null) {
            return unavailable(context);
        }
        answer(context, "Reminder");
        runAnswered(context, () -> {
            notes.get(context.userId(), id);
            clearOwn(context);
            present(context, formatter.askWhen(), reminderKeyboard(id, origin));
        });
        return true;
    }

    private boolean remindPreset(BotContext context, Long id, Instant when) {
        if (id == null) {
            return unavailable(context);
        }
        if (!when.isAfter(times.now())) {
            telegram.answerCallback(context.callbackId(), NoteMessages.past(), true);
            return true;
        }
        answer(context, "Reminder");
        runAnswered(context, () -> scheduleAt(context, id, when));
        return true;
    }

    private boolean askDate(BotContext context, Long id, String origin) {
        if (id == null) {
            return unavailable(context);
        }
        answer(context, "Reminder");
        runAnswered(context, () -> {
            notes.get(context.userId(), id);
            ConversationStateEntity state = fresh(context, ConversationStep.NOTES_WAITING_REMINDER_DATE, "date");
            state.setPendingRange(Long.toString(id));
            conversations.save(state);
            present(context, NoteMessages.askDate(),
                    List.of(List.of(button(Texts.t("Cancel", "បោះបង់"), openData(id, origin)))));
        });
        return true;
    }

    private boolean clearReminder(BotContext context, Long id, String origin) {
        if (id == null) {
            return unavailable(context);
        }
        answer(context, "Reminder");
        runAnswered(context, () -> {
            notes.cancelReminder(context.userId(), id);
            clearOwn(context);
            renderDetail(context, id, origin);
        });
        return true;
    }

    private void scheduleAt(BotContext context, long noteId, Instant when) {
        NoteService.Scheduled scheduled = notes.schedule(context.userId(), context.chatId(), noteId, when);
        conversations.clear(context.chatId());
        String text = formatter.scheduled(scheduled.note(), scheduled.reminder().getRemindAt());
        if (context.callbackId() != null && context.messageId() != null) {
            telegram.editMessage(context.chatId(), context.messageId(), text, confirmationKeyboard(noteId));
        } else {
            telegram.sendMessage(context.chatId(), text, confirmationKeyboard(noteId));
        }
    }

    private void renderDetail(BotContext context, long id, String origin) {
        NoteEntity note = notes.get(context.userId(), id);
        List<NoteTagEntity> tags = notes.tags(context.userId(), id);
        NoteReminderEntity reminder = notes.pendingReminder(context.userId(), id).orElse(null);
        present(context, formatter.detail(note, tags, reminder), detailKeyboard(context, note, tags, reminder, origin));
    }

    private void showCollection(BotContext context, String text, Page<NoteEntity> page, String originPrefix,
                                String pagePrefix) {
        List<List<Map<String, String>>> rows = new ArrayList<>();
        int index = 0;
        for (NoteEntity note : page.getContent()) {
            if (note.getId() == null) {
                index++;
                continue;
            }
            int number = page.getNumber() * NoteService.PAGE_SIZE + index + 1;
            String label = number + ". " + (note.isPinned() ? "📌 " : "📝 ")
                    + NoteFormatter.preview(note.getTitle(), 24);
            rows.add(List.of(button(label, openData(note.getId(), originPrefix + page.getNumber()))));
            index++;
        }
        List<Map<String, String>> nav = new ArrayList<>();
        if (page.getNumber() > 0) {
            nav.add(button(Texts.t("⬅️ Previous", "⬅️ មុន"), pagePrefix + (page.getNumber() - 1)));
        }
        if (page.getNumber() + 1 < page.getTotalPages()) {
            nav.add(button(Texts.t("Next ➡️", "បន្ទាប់ ➡️"), pagePrefix + (page.getNumber() + 1)));
        }
        if (!nav.isEmpty()) {
            rows.add(nav);
        }
        if (context.allows(CREATE)) {
            rows.add(row("➕ New Note", "➕ កំណត់ចំណាំថ្មី", "note:new"));
        }
        rows.add(row("⬅️ Notes Menu", "⬅️ ម៉ឺនុយកំណត់ចំណាំ", "note:menu"));
        present(context, text, rows);
    }

    private List<List<Map<String, String>>> detailKeyboard(BotContext context, NoteEntity note,
                                                           List<NoteTagEntity> tags, NoteReminderEntity reminder,
                                                           String origin) {
        List<List<Map<String, String>>> rows = new ArrayList<>();
        List<Map<String, String>> editPin = new ArrayList<>();
        if (context.allows(EDIT)) {
            editPin.add(button(Texts.t("✏️ Edit", "✏️ កែ"), "note:edit:" + note.getId()));
        }
        if (context.allows(PIN)) {
            if (note.isPinned()) {
                editPin.add(button(Texts.t("📌 Unpin", "📌 ដោះខ្ទាស់"),
                        withOrigin("note:unpin", note.getId(), origin)));
            } else {
                editPin.add(button(Texts.t("📌 Pin", "📌 ខ្ទាស់"), withOrigin("note:pin", note.getId(), origin)));
            }
        }
        if (!editPin.isEmpty()) {
            rows.add(editPin);
        }
        if (context.allows(REMINDER_CREATE)) {
            rows.add(List.of(button(Texts.t("⏰ Remind Me", "⏰ រំលឹកខ្ញុំ"),
                    withOrigin("note:rem", note.getId(), origin))));
        }
        if (reminder != null && context.allows(REMINDER_CANCEL)) {
            rows.add(List.of(button(Texts.t("Cancel reminder", "បោះបង់ការរំលឹក"),
                    withOrigin("note:remclr", note.getId(), origin))));
        }
        if (context.allows(DELETE)) {
            rows.add(List.of(button(Texts.t("🗑 Delete", "🗑 លុប"), withOrigin("note:del", note.getId(), origin))));
        }
        if (context.allows(SEARCH) && tags != null) {
            for (NoteTagEntity tag : tags) {
                if (tag.getId() == null) {
                    continue;
                }
                String label = "#" + tag.getTagName();
                if (label.length() > 30) {
                    label = label.substring(0, 30);
                }
                rows.add(List.of(button(label, "note:tag:" + tag.getId() + ":0")));
            }
        }
        rows.add(List.of(button(Texts.t("⬅️ Back", "⬅️ ថយក្រោយ"), backData(origin))));
        return rows;
    }

    private List<List<Map<String, String>>> reminderKeyboard(long id, String origin) {
        return List.of(
                List.of(button(Texts.t("Today", "ថ្ងៃនេះ"), withOrigin("note:remtoday", id, origin))),
                List.of(button(Texts.t("Tomorrow", "ថ្ងៃស្អែក"), withOrigin("note:remtom", id, origin))),
                List.of(button(Texts.t("Choose Date", "ជ្រើសថ្ងៃ"), withOrigin("note:remdate", id, origin))),
                List.of(button(Texts.t("Cancel", "បោះបង់"), openData(id, origin))));
    }

    private List<List<Map<String, String>>> confirmationKeyboard(long id) {
        return List.of(
                List.of(button(Texts.t("View Note", "មើលកំណត់ចំណាំ"), openData(id, "l0"))),
                List.of(button(Texts.t("⬅️ Notes Menu", "⬅️ ម៉ឺនុយកំណត់ចំណាំ"), "note:menu")));
    }

    private List<List<Map<String, String>>> viewKeyboard(Long id) {
        if (id == null) {
            return List.of();
        }
        return confirmationKeyboard(id);
    }

    private void rememberSearch(BotContext context, String keyword) {
        ConversationStateEntity state = conversations.get(context.chatId()).orElse(null);
        if (state == null || state.getUserId() == null || !state.getUserId().equals(context.userId())) {
            state = fresh(context, ConversationStep.NOTES_WAITING_SEARCH_QUERY, "page");
        }
        state.setStep(ConversationStep.NOTES_WAITING_SEARCH_QUERY);
        state.setModuleKey(ID);
        state.setAction("page");
        state.setTitle(keyword);
        conversations.save(state);
    }

    private void retainSearch(BotContext context) {
        Optional<ConversationStateEntity> found = conversations.get(context.chatId());
        if (found.isEmpty()) {
            return;
        }
        ConversationStateEntity state = found.get();
        if (context.userId() == null || !context.userId().equals(state.getUserId())) {
            return;
        }
        if (state.getStep() == ConversationStep.NOTES_WAITING_SEARCH_QUERY
                && "page".equals(state.getAction())
                && state.getTitle() != null
                && !state.getTitle().isBlank()) {
            conversations.save(state);
            return;
        }
        conversations.clear(context.chatId());
    }

    private void clearOwn(BotContext context) {
        conversations.get(context.chatId()).ifPresent(state -> {
            if (context.userId() != null && context.userId().equals(state.getUserId())) {
                conversations.clear(context.chatId());
            }
        });
    }

    private ConversationStateEntity fresh(BotContext context, ConversationStep step, String action) {
        ConversationStateEntity state = new ConversationStateEntity(
                context.chatId(), context.userId(), step, times.now());
        state.setModuleKey(ID);
        state.setAction(action);
        return state;
    }

    private static boolean searchReady(BotContext context, ConversationLookup lookup) {
        if (lookup == null || lookup.kind() != ConversationLookup.Kind.FOUND || lookup.state() == null) {
            return false;
        }
        ConversationStateEntity state = lookup.state();
        return context.userId() != null
                && context.userId().equals(state.getUserId())
                && state.getStep() == ConversationStep.NOTES_WAITING_SEARCH_QUERY
                && state.getTitle() != null
                && !state.getTitle().isBlank();
    }

    private void present(BotContext context, String text, List<List<Map<String, String>>> keyboard) {
        if (context.callbackId() != null && context.messageId() != null) {
            telegram.editMessage(context.chatId(), context.messageId(), text, keyboard);
            return;
        }
        telegram.sendMessage(context.chatId(), text, keyboard);
    }

    private void answer(BotContext context, String text) {
        telegram.answerCallback(context.callbackId(), text, false);
    }

    private void runAnswered(BotContext context, Runnable action) {
        try {
            action.run();
        } catch (AccessDeniedException exception) {
            telegram.sendMessage(context.chatId(), AccessMessages.actionUnavailable());
        } catch (IllegalArgumentException exception) {
            telegram.sendMessage(context.chatId(), exception.getMessage());
        }
    }

    private boolean unavailable(BotContext context) {
        telegram.answerCallback(context.callbackId(), NoteMessages.buttonGone(), true);
        return true;
    }

    private String backData(String origin) {
        Origin parsed = parseOrigin(origin);
        return switch (parsed.kind) {
            case 'p' -> "note:pinlist:" + parsed.page;
            case 'q' -> "note:q:" + parsed.page;
            case 'g' -> parsed.tagId == null
                    ? "note:list:0"
                    : "note:tag:" + parsed.tagId + ":" + parsed.page;
            default -> "note:list:" + parsed.page;
        };
    }

    private static Origin parseOrigin(String token) {
        String value = token == null || token.isBlank() ? "l0" : token;
        char kind = value.charAt(0);
        String rest = value.length() > 1 ? value.substring(1) : "";
        try {
            if (kind == 'g') {
                int split = rest.indexOf('_');
                if (split <= 0) {
                    return new Origin('l', 0, null);
                }
                long tagId = Long.parseLong(rest.substring(0, split));
                int page = Integer.parseInt(rest.substring(split + 1));
                if (page < 0) {
                    return new Origin('l', 0, null);
                }
                return new Origin('g', page, tagId);
            }
            if (kind != 'l' && kind != 'p' && kind != 'q') {
                return new Origin('l', 0, null);
            }
            int page = rest.isEmpty() ? 0 : Integer.parseInt(rest);
            if (page < 0) {
                return new Origin('l', 0, null);
            }
            return new Origin(kind, page, null);
        } catch (NumberFormatException exception) {
            return new Origin('l', 0, null);
        }
    }

    private static String openData(long id, String origin) {
        return fit("note:open:" + id + ":" + (origin == null || origin.isBlank() ? "l0" : origin),
                "note:open:" + id + ":l0");
    }

    private static String withOrigin(String prefix, long id, String origin) {
        String safe = origin == null || origin.isBlank() ? "l0" : origin;
        return fit(prefix + ":" + id + ":" + safe, prefix + ":" + id);
    }

    private static String fit(String full, String shorter) {
        if (full.getBytes(StandardCharsets.UTF_8).length <= 64) {
            return full;
        }
        return shorter;
    }

    private static Long pendingId(ConversationStateEntity state) {
        if (state == null || state.getPendingRange() == null) {
            return null;
        }
        try {
            return Long.valueOf(state.getPendingRange());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Long longAt(String[] parts, int index) {
        String value = partAt(parts, index);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Integer pageAt(String[] parts, int index) {
        if (parts.length <= index || parts[index] == null || parts[index].isBlank()) {
            return 0;
        }
        try {
            int page = Integer.parseInt(parts[index]);
            return page < 0 ? null : page;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String partAt(String[] parts, int index) {
        if (parts.length <= index) {
            return null;
        }
        return parts[index];
    }

    private List<Map<String, String>> row(String english, String khmer, String data) {
        return List.of(button(Texts.t(english, khmer), data));
    }

    private List<List<Map<String, String>>> cancelKeyboard() {
        return List.of(row("Cancel", "បោះបង់", "note:cancel"));
    }

    private static Map<String, String> button(String text, String data) {
        return Map.of("text", text, "callback_data", data);
    }

    private static String menuCallback(MenuAction action) {
        return switch (action.action()) {
            case "new" -> "note:new";
            case "list" -> "note:list:0";
            case "search" -> "note:search";
            case "pinned" -> "note:pinlist:0";
            default -> "note:menu";
        };
    }

    private static FeatureDeclaration open(String key) {
        return new FeatureDeclaration(key, true);
    }

    private static String normalize(String command) {
        return command == null ? "" : command.toLowerCase(Locale.ROOT);
    }

    @FunctionalInterface
    private interface PageAction {
        void show(BotContext context, int page);
    }

    private record Origin(char kind, int page, Long tagId) {
    }
}
