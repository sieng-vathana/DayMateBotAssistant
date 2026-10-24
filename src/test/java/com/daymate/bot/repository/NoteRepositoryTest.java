package com.daymate.bot.repository;

import com.daymate.bot.entity.NoteEntity;
import com.daymate.bot.entity.NoteReminderEntity;
import com.daymate.bot.entity.NoteTagEntity;
import com.daymate.bot.notes.NoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class NoteRepositoryTest {
    private static final Sort NOTE_SORT = Sort.by(
            Sort.Order.desc("pinned"),
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id"));

    @Autowired
    private NoteRepository notes;
    @Autowired
    private NoteTagRepository tags;
    @Autowired
    private NoteReminderRepository reminders;

    @Test
    void listsOnlyTheUsersLiveNotesWithPinnedFirst() {
        NoteEntity pinned = save(1L, "Older pin", "Older pin", true, Instant.parse("2026-10-01T00:00:00Z"), null);
        NoteEntity fresh = save(1L, "Newer", "Newer", false, Instant.parse("2026-10-20T00:00:00Z"), null);
        save(1L, "Gone", "Gone", false, Instant.parse("2026-10-21T00:00:00Z"), Instant.parse("2026-10-22T00:00:00Z"));
        save(2L, "Other", "Other", true, Instant.parse("2026-10-23T00:00:00Z"), null);

        Page<NoteEntity> page = notes.findByTelegramUserIdAndDeletedAtIsNull(
                1L, PageRequest.of(0, NoteService.PAGE_SIZE, NOTE_SORT));

        assertThat(page.getContent()).extracting(NoteEntity::getId).containsExactly(pinned.getId(), fresh.getId());
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void searchMatchesKhmerTagsAndEscapesUnderscore() {
        NoteEntity khmer = save(1L, "សួស្តី", "ជួបសួស្តី", false, Instant.parse("2026-10-02T00:00:00Z"), null);
        NoteEntity tagged = save(1L, "hello", "hello", false, Instant.parse("2026-10-03T00:00:00Z"), null);
        tags.save(new NoteTagEntity(tagged.getId(), "java"));
        NoteEntity literal = save(1L, "a_b", "a_b", false, Instant.parse("2026-10-04T00:00:00Z"), null);
        save(1L, "axb", "axb", false, Instant.parse("2026-10-05T00:00:00Z"), null);
        save(2L, "សួស្តី", "សួស្តី", false, Instant.parse("2026-10-06T00:00:00Z"), null);
        NoteEntity deleted = save(1L, "សួស្តី", "សួស្តី", false, Instant.parse("2026-10-07T00:00:00Z"),
                Instant.parse("2026-10-08T00:00:00Z"));
        tags.save(new NoteTagEntity(deleted.getId(), "សួស្តី"));

        Page<NoteEntity> khmerPage = notes.search(1L, "%សួស្តី%", PageRequest.of(0, 5, NOTE_SORT));
        Page<NoteEntity> tagPage = notes.search(1L, "%java%", PageRequest.of(0, 5, NOTE_SORT));
        Page<NoteEntity> escaped = notes.search(1L, "%a!_b%", PageRequest.of(0, 5, NOTE_SORT));

        assertThat(khmerPage.getContent()).extracting(NoteEntity::getId).containsExactly(khmer.getId());
        assertThat(tagPage.getContent()).extracting(NoteEntity::getId).containsExactly(tagged.getId());
        assertThat(escaped.getContent()).extracting(NoteEntity::getId).containsExactly(literal.getId());
    }

    @Test
    void pagesFiveNotesAtATime() {
        for (int i = 0; i < 6; i++) {
            save(1L, "n" + i, "n" + i, false, Instant.parse("2026-10-01T00:00:00Z").plusSeconds(i), null);
        }

        Page<NoteEntity> first = notes.findByTelegramUserIdAndDeletedAtIsNull(
                1L, PageRequest.of(0, 5, NOTE_SORT));
        Page<NoteEntity> second = notes.findByTelegramUserIdAndDeletedAtIsNull(
                1L, PageRequest.of(1, 5, NOTE_SORT));

        assertThat(first.getContent()).hasSize(5);
        assertThat(first.getTotalElements()).isEqualTo(6);
        assertThat(second.getContent()).hasSize(1);
    }

    @Test
    void dueRemindersAreTheOldestFiftyPendingRows() {
        Instant base = Instant.parse("2026-10-01T00:00:00Z");
        for (int i = 0; i < 51; i++) {
            reminders.save(new NoteReminderEntity(1L, 1L, 1L, base.plusSeconds(i), base));
        }
        NoteReminderEntity sent = reminders.save(new NoteReminderEntity(1L, 1L, 1L, base.minusSeconds(5), base));
        sent.setStatus(NoteReminderEntity.SENT);
        reminders.save(new NoteReminderEntity(1L, 2L, 2L, base.plusSeconds(80), base));

        List<NoteReminderEntity> due = reminders.findTop50ByStatusAndRemindAtLessThanEqualOrderByRemindAtAsc(
                NoteReminderEntity.PENDING, base.plusSeconds(80));

        assertThat(due).hasSize(50);
        assertThat(due.get(0).getRemindAt()).isEqualTo(base);
        assertThat(due).extracting(NoteReminderEntity::getRemindAt).doesNotContain(base.plusSeconds(50));
        assertThat(due).noneMatch(row -> NoteReminderEntity.SENT.equals(row.getStatus()));
    }

    private NoteEntity save(Long userId, String title, String content, boolean pinned, Instant created, Instant deleted) {
        NoteEntity note = new NoteEntity(userId, title, content, pinned, created);
        if (deleted != null) {
            note.setDeletedAt(deleted);
        }
        return notes.saveAndFlush(note);
    }
}
