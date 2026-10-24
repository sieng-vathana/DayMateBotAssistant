package com.daymate.bot.repository;

import com.daymate.bot.entity.NoteEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NoteRepository extends JpaRepository<NoteEntity, Long> {
    Page<NoteEntity> findByTelegramUserIdAndDeletedAtIsNull(Long telegramUserId, Pageable pageable);

    Page<NoteEntity> findByTelegramUserIdAndDeletedAtIsNullAndPinnedIsTrue(Long telegramUserId, Pageable pageable);

    Optional<NoteEntity> findByIdAndTelegramUserIdAndDeletedAtIsNull(Long id, Long telegramUserId);

    @Query(value = """
            select n from NoteEntity n
            where n.telegramUserId = :userId
              and n.deletedAt is null
              and (
                lower(n.title) like lower(:pattern) escape '!'
                or lower(n.content) like lower(:pattern) escape '!'
                or exists (
                  select 1 from NoteTagEntity t
                  where t.noteId = n.id and lower(t.tagName) like lower(:pattern) escape '!'
                )
              )
            """,
            countQuery = """
            select count(n) from NoteEntity n
            where n.telegramUserId = :userId
              and n.deletedAt is null
              and (
                lower(n.title) like lower(:pattern) escape '!'
                or lower(n.content) like lower(:pattern) escape '!'
                or exists (
                  select 1 from NoteTagEntity t
                  where t.noteId = n.id and lower(t.tagName) like lower(:pattern) escape '!'
                )
              )
            """)
    Page<NoteEntity> search(@Param("userId") Long userId, @Param("pattern") String pattern, Pageable pageable);

    @Query(value = """
            select n from NoteEntity n
            where n.telegramUserId = :userId
              and n.deletedAt is null
              and exists (
                select 1 from NoteTagEntity t
                where t.noteId = n.id and t.tagName = :tagName
              )
            """,
            countQuery = """
            select count(n) from NoteEntity n
            where n.telegramUserId = :userId
              and n.deletedAt is null
              and exists (
                select 1 from NoteTagEntity t
                where t.noteId = n.id and t.tagName = :tagName
              )
            """)
    Page<NoteEntity> findByTagName(@Param("userId") Long userId, @Param("tagName") String tagName, Pageable pageable);
}
