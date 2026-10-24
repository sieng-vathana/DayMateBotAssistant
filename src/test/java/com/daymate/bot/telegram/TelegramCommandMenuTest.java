package com.daymate.bot.telegram;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramCommandMenuTest {
    @Mock
    private TelegramApiClient telegram;

    @Test
    void publishesNoteCommandsWithoutHyphens() {
        when(telegram.isConfigured()).thenReturn(true);

        new TelegramCommandMenu(telegram).run(null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, String>>> commands = ArgumentCaptor.forClass(List.class);
        verify(telegram).setMyCommands(commands.capture());
        assertThat(commands.getValue())
                .extracting(command -> command.get("command"))
                .contains("note", "notes", "my_notes", "search", "pinned", "ot", "help");
        assertThat(commands.getValue())
                .allSatisfy(command -> assertThat(command.get("command")).matches("[a-z0-9_]{1,32}"));
    }

    @Test
    void skipsPublicationWhenTheBotTokenIsEmpty() {
        when(telegram.isConfigured()).thenReturn(false);

        new TelegramCommandMenu(telegram).run(null);

        verify(telegram, never()).setMyCommands(org.mockito.ArgumentMatchers.any());
    }
}
