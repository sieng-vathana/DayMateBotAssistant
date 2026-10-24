package com.daymate.bot.notes;

import com.daymate.bot.core.access.AccessDecision;
import com.daymate.bot.core.access.AccessDeniedException;
import com.daymate.bot.core.access.AccessMessages;
import com.daymate.bot.core.access.FeatureAccessGuard;
import com.daymate.bot.entity.NoteEntity;
import com.daymate.bot.entity.NoteReminderEntity;
import com.daymate.bot.entity.NoteTagEntity;
import com.daymate.bot.modules.notes.NotesModule;
import com.daymate.bot.repository.NoteReminderRepository;
import com.daymate.bot.repository.NoteRepository;
import com.daymate.bot.repository.NoteTagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;

@Service
@Transactional
public class NoteService {
    public static final int PAGE_SIZE = 5;
    public static final int MAX_PAGE = 200;

    private static final Logger log = LoggerFactory.getLogger(NoteService.class);
    private static final Sort NOTE_SORT = Sort.by(
            Sort.Order.desc("pinned"),
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id"));

    private final FeatureAccessGuard guard;
    private final NoteRepository notes;
    private final NoteTagRepository tags;
    private final NoteReminderRepository reminders;
    private final Clock clock;

    public NoteService(@Lazy FeatureAccessGuard guard, NoteRepository notes, NoteTagRepository tags,
                       NoteReminderRepository reminders, Clock clock) {
        this.guard = guard;
        this.notes = notes;
        this.tags = tags;
        this.reminders = reminders;
        this.clock = clock;
    }

    public NoteEntity create(Long userId, Long chatId, String content) {
        require(userId, NotesModule.CREATE);
        String body = NoteText.content(content);
        NoteEntity saved = notes.save(new NoteEntity(userId, NoteText.title(body), body, false, Instant.now(clock)));
        replaceTags(saved.getId(), body);
        log.info("Saved note {} for user {}", saved.getId(), userId);
        return saved;
    }

    public Page<NoteEntity> list(Long userId, int page) {
        require(userId, NotesModule.VIEW);
        int requested = clampPage(page);
        return lastPage(requested, notes.findByTelegramUserIdAndDeletedAtIsNull(userId, pageable(requested)),
                index -> notes.findByTelegramUserIdAndDeletedAtIsNull(userId, pageable(index)));
    }

    public Page<NoteEntity> pinned(Long userId, int page) {
        require(userId, NotesModule.PIN);
        int requested = clampPage(page);
        return lastPage(requested,
                notes.findByTelegramUserIdAndDeletedAtIsNullAndPinnedIsTrue(userId, pageable(requested)),
                index -> notes.findByTelegramUserIdAndDeletedAtIsNullAndPinnedIsTrue(userId, pageable(index)));
    }

    public Page<NoteEntity> search(Long userId, String keyword, int page) {
        require(userId, NotesModule.SEARCH);
        String cleaned = NoteText.keyword(keyword);
        String pattern = NoteText.likePattern(cleaned);
        int requested = clampPage(page);
        return lastPage(requested, notes.search(userId, pattern, pageable(requested)),
                index -> notes.search(userId, pattern, pageable(index)));
    }

    public String ownedTagName(Long userId, long tagId) {
        return ownedTag(userId, tagId).getTagName();
    }

    public Page<NoteEntity> byTag(Long userId, long tagId, int page) {
        String name = ownedTag(userId, tagId).getTagName();
        int requested = clampPage(page);
        return lastPage(requested, notes.findByTagName(userId, name, pageable(requested)),
                index -> notes.findByTagName(userId, name, pageable(index)));
    }

    public NoteEntity get(Long userId, long noteId) {
        require(userId, NotesModule.VIEW);
        return requireActive(userId, noteId);
    }

    public List<NoteTagEntity> tags(Long userId, long noteId) {
        require(userId, NotesModule.VIEW);
        requireActive(userId, noteId);
        return tags.findByNoteIdOrderByIdAsc(noteId);
    }

    public NoteEntity update(Long userId, long noteId, String content) {
        require(userId, NotesModule.EDIT);
        String body = NoteText.content(content);
        NoteEntity note = requireActive(userId, noteId);
        note.setTitle(NoteText.title(body));
        note.setContent(body);
        note.setUpdatedAt(Instant.now(clock));
        NoteEntity saved = notes.save(note);
        replaceTags(saved.getId(), body);
        log.info("Updated note {} for user {}", saved.getId(), userId);
        return saved;
    }

    public void softDelete(Long userId, long noteId) {
        require(userId, NotesModule.DELETE);
        NoteEntity note = requireActive(userId, noteId);
        note.setDeletedAt(Instant.now(clock));
        note.setUpdatedAt(Instant.now(clock));
        notes.save(note);
        log.info("Deleted note {} for user {}", noteId, userId);
    }

    public NoteEntity setPinned(Long userId, long noteId, boolean pinned) {
        require(userId, NotesModule.PIN);
        NoteEntity note = requireActive(userId, noteId);
        note.setPinned(pinned);
        note.setUpdatedAt(Instant.now(clock));
        log.info("Set pin {} on note {} for user {}", pinned, noteId, userId);
        return notes.save(note);
    }

    public Scheduled schedule(Long userId, Long chatId, long noteId, Instant remindAt) {
        require(userId, NotesModule.REMINDER_CREATE);
        NoteEntity note = requireActive(userId, noteId);
        if (remindAt == null || !remindAt.isAfter(Instant.now(clock))) {
            throw new IllegalArgumentException(NoteMessages.past());
        }
        for (NoteReminderEntity pending : reminders.findByNoteIdAndTelegramUserIdAndStatus(
                noteId, userId, NoteReminderEntity.PENDING)) {
            pending.setStatus(NoteReminderEntity.CANCELLED);
        }
        NoteReminderEntity saved = reminders.save(new NoteReminderEntity(
                noteId, userId, chatId, remindAt, Instant.now(clock)));
        log.info("Scheduled reminder {} on note {} for user {}", saved.getId(), noteId, userId);
        return new Scheduled(note, saved);
    }

    public void cancelReminder(Long userId, long noteId) {
        require(userId, NotesModule.REMINDER_CANCEL);
        requireActive(userId, noteId);
        List<NoteReminderEntity> pending = reminders.findByNoteIdAndTelegramUserIdAndStatus(
                noteId, userId, NoteReminderEntity.PENDING);
        if (pending.isEmpty()) {
            throw new IllegalArgumentException(NoteMessages.noReminder());
        }
        for (NoteReminderEntity reminder : pending) {
            reminder.setStatus(NoteReminderEntity.CANCELLED);
        }
        log.info("Cancelled reminder on note {} for user {}", noteId, userId);
    }

    public Optional<NoteReminderEntity> pendingReminder(Long userId, long noteId) {
        require(userId, NotesModule.VIEW);
        requireActive(userId, noteId);
        return reminders.findByNoteIdAndTelegramUserIdAndStatus(noteId, userId, NoteReminderEntity.PENDING)
                .stream()
                .findFirst();
    }

    public List<NoteReminderEntity> dueReminders(Instant now) {
        return reminders.findTop50ByStatusAndRemindAtLessThanEqualOrderByRemindAtAsc(
                NoteReminderEntity.PENDING, now);
    }

    public boolean markSent(Long userId, Long reminderId, Instant sentAt) {
        return reminders.findByIdAndTelegramUserId(reminderId, userId)
                .filter(row -> NoteReminderEntity.PENDING.equals(row.getStatus()))
                .map(row -> {
                    row.setStatus(NoteReminderEntity.SENT);
                    row.setSentAt(sentAt);
                    log.info("Sent reminder {} for user {}", reminderId, userId);
                    return true;
                })
                .orElse(false);
    }

    public void markSkipped(Long reminderId) {
        reminders.findById(reminderId).ifPresent(row -> {
            if (NoteReminderEntity.PENDING.equals(row.getStatus())) {
                row.setStatus(NoteReminderEntity.SKIPPED);
                log.info("Skipped reminder {} for user {}", reminderId, row.getTelegramUserId());
            }
        });
    }

    public Optional<NoteEntity> activeNote(Long userId, Long noteId) {
        if (userId == null || noteId == null) {
            return Optional.empty();
        }
        return notes.findByIdAndTelegramUserIdAndDeletedAtIsNull(noteId, userId);
    }

    public Optional<NoteEntity> activeForDelivery(NoteReminderEntity reminder) {
        if (reminder == null) {
            return Optional.empty();
        }
        if (guard.decide(reminder.getTelegramUserId(), NotesModule.ID, NotesModule.REMINDER_CREATE)
                != AccessDecision.ALLOW) {
            return Optional.empty();
        }
        return activeNote(reminder.getTelegramUserId(), reminder.getNoteId());
    }

    private void replaceTags(Long noteId, String content) {
        List<String> parsed = NoteText.tags(content);
        if (parsed.isEmpty() || noteId == null) {
            return;
        }
        tags.deleteAll(tags.findByNoteIdOrderByIdAsc(noteId));
        tags.flush();
        for (String name : parsed) {
            tags.save(new NoteTagEntity(noteId, name));
        }
    }

    private NoteTagEntity ownedTag(Long userId, long tagId) {
        require(userId, NotesModule.SEARCH);
        NoteTagEntity tag = tags.findById(tagId).orElseThrow(() -> new IllegalArgumentException(NoteMessages.gone()));
        notes.findByIdAndTelegramUserIdAndDeletedAtIsNull(tag.getNoteId(), userId)
                .orElseThrow(() -> new IllegalArgumentException(NoteMessages.gone()));
        return tag;
    }

    private NoteEntity requireActive(Long userId, long noteId) {
        return notes.findByIdAndTelegramUserIdAndDeletedAtIsNull(noteId, userId)
                .orElseThrow(() -> new IllegalArgumentException(NoteMessages.gone()));
    }

    private void require(Long userId, String feature) {
        if (guard.decide(userId, NotesModule.ID, feature) != AccessDecision.ALLOW) {
            throw new AccessDeniedException(AccessMessages.actionUnavailable());
        }
    }

    private static Page<NoteEntity> lastPage(int requested, Page<NoteEntity> result, IntFunction<Page<NoteEntity>> again) {
        if (result.getTotalElements() == 0) {
            return result;
        }
        int last = result.getTotalPages() - 1;
        if (last >= 0 && requested > last) {
            return again.apply(Math.min(last, MAX_PAGE));
        }
        return result;
    }

    private static int clampPage(int page) {
        if (page < 0) {
            return 0;
        }
        return Math.min(page, MAX_PAGE);
    }

    private static Pageable pageable(int page) {
        return PageRequest.of(page, PAGE_SIZE, NOTE_SORT);
    }

    public record Scheduled(NoteEntity note, NoteReminderEntity reminder) {
    }
}
