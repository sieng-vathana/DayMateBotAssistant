package com.daymate.bot.portal;

import com.daymate.bot.entity.NoteEntity;
import com.daymate.bot.entity.NoteReminderEntity;
import com.daymate.bot.entity.PortalUserEntity;
import com.daymate.bot.entity.NoteTagEntity;
import com.daymate.bot.notes.NoteService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
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

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/portal/notes")
public class PortalNotesController {
    private final NoteService notes;

    public PortalNotesController(NoteService notes) {
        this.notes = notes;
    }

    @GetMapping
    public NotePage list(HttpServletRequest request,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(required = false) String query,
                         @RequestParam(defaultValue = "false") boolean pinned) {
        Long userId = userId(request);
        Page<NoteEntity> result;
        if (query != null && !query.isBlank()) {
            result = notes.search(userId, query, page);
        } else if (pinned) {
            result = notes.pinned(userId, page);
        } else {
            result = notes.list(userId, page);
        }
        return new NotePage(
                result.getContent().stream().map(note -> view(userId, note)).toList(),
                result.getNumber(), result.getTotalPages(), result.getTotalElements(), result.hasNext());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NoteView create(HttpServletRequest request, @RequestBody NoteBody body) {
        Long userId = userId(request);
        return view(userId, notes.create(userId, userId, body.content()));
    }

    @PutMapping("/{id}")
    public NoteView update(HttpServletRequest request, @PathVariable long id, @RequestBody NoteBody body) {
        Long userId = userId(request);
        return view(userId, notes.update(userId, id, body.content()));
    }

    @PutMapping("/{id}/pin")
    public NoteView pin(HttpServletRequest request, @PathVariable long id, @RequestBody PinnedBody body) {
        Long userId = userId(request);
        return view(userId, notes.setPinned(userId, id, Boolean.TRUE.equals(body.pinned())));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(HttpServletRequest request, @PathVariable long id) {
        notes.softDelete(userId(request), id);
    }

    @PostMapping("/{id}/reminder")
    public NoteView remind(HttpServletRequest request, @PathVariable long id, @RequestBody ReminderBody body) {
        Long userId = userId(request);
        Instant remindAt;
        try {
            remindAt = Instant.parse(body.remindAt());
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new IllegalArgumentException("remindAt must be an ISO-8601 timestamp");
        }
        NoteService.Scheduled scheduled = notes.schedule(userId, userId, id, remindAt);
        return view(userId, scheduled.note());
    }

    @DeleteMapping("/{id}/reminder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelReminder(HttpServletRequest request, @PathVariable long id) {
        notes.cancelReminder(userId(request), id);
    }

    private NoteView view(Long userId, NoteEntity note) {
        Instant reminder = notes.pendingReminder(userId, note.getId())
                .map(NoteReminderEntity::getRemindAt)
                .orElse(null);
        List<String> tags = notes.tags(userId, note.getId()).stream().map(NoteTagEntity::getTagName).toList();
        return new NoteView(note.getId(), note.getTitle(), note.getContent(), note.isPinned(),
                note.getCreatedAt(), note.getUpdatedAt(), reminder, tags);
    }

    private static Long userId(HttpServletRequest request) {
        PortalUserEntity user = (PortalUserEntity) request.getAttribute(PortalAuthFilter.USER);
        return PortalIdentity.requireLinkedTelegramUserId(user);
    }

    public record NoteBody(String content) {
    }

    public record PinnedBody(Boolean pinned) {
    }

    public record ReminderBody(String remindAt) {
    }

    public record NoteView(Long id, String title, String content, boolean pinned,
                           Instant createdAt, Instant updatedAt, Instant remindAt, List<String> tags) {
    }

    public record NotePage(List<NoteView> items, int page, int totalPages, long totalElements, boolean hasNext) {
    }
}
