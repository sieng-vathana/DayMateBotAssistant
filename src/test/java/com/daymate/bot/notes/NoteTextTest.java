package com.daymate.bot.notes;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoteTextTest {
    @Test
    void keepsInternalLinesAndBuildsATitleFromTheFirstLine() {
        String content = NoteText.content("  hello  \nworld  ");

        assertThat(content).isEqualTo("hello  \nworld");
        assertThat(NoteText.title(content)).isEqualTo("hello");
    }

    @Test
    void rejectsEmptyAndTooLongNotes() {
        assertThatThrownBy(() -> NoteText.content(" \n "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Send the note text.");
        assertThatThrownBy(() -> NoteText.content("x".repeat(1001)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Send a shorter note.");
    }

    @Test
    void parsesTheFirstEightUniqueTagsAndKeepsThemInTheText() {
        String content = "See ##java and #Java plus #work_1 #a #b #c #d #e #f #extra";

        assertThat(NoteText.tags(content)).containsExactly("java", "work_1", "a", "b", "c", "d", "e", "f");
        assertThat(content).contains("#Java");
    }

    @Test
    void limitsSearchCharacters() {
        assertThat(NoteText.keyword("  #Java  ")).isEqualTo("#Java");
        assertThat(NoteText.likePattern("#Java")).isEqualTo("%#java%");
        assertThat(NoteText.keyword("a_b")).isEqualTo("a_b");
        assertThat(NoteText.likePattern("a_b")).isEqualTo("%a!_b%");
        assertThatThrownBy(() -> NoteText.keyword("a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Send a search of 2 to 40 characters.");
        assertThatThrownBy(() -> NoteText.keyword("100%"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NoteText.keyword("no!"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NoteText.keyword("no\npe"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void titleStopsAtEightyCharacters() {
        String content = "a".repeat(90);
        assertThat(NoteText.title(content)).hasSize(80);
    }
}
