package com.daymate.bot.notes;

import java.lang.reflect.Field;

public final class NoteFixtures {
    private NoteFixtures() {
    }

    public static void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
