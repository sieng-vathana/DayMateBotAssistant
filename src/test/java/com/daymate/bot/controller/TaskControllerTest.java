package com.daymate.bot.controller;

import com.daymate.bot.DayMateBotApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = DayMateBotApplication.class)
@AutoConfigureMockMvc
@Transactional
class TaskControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthIsUp() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void createAndFetchTodayAgenda() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "telegramUserId": 42,
                                  "chatId": 42,
                                  "title": "Buy groceries",
                                  "category": "PERSONAL",
                                  "priority": "MEDIUM",
                                  "dueTime": "5:00 PM",
                                  "recurrence": "NONE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publicId").value("T-001"))
                .andExpect(jsonPath("$.title").value("Buy groceries"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(get("/api/tasks/today").param("telegramUserId", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.doneCount").value(0))
                .andExpect(jsonPath("$.remaining", hasSize(1)))
                .andExpect(jsonPath("$.remaining[0].publicId").value("T-001"));

        mockMvc.perform(post("/api/tasks/T-001/complete").param("telegramUserId", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(get("/api/tasks/today").param("telegramUserId", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doneCount").value(1))
                .andExpect(jsonPath("$.remaining", hasSize(0)));
    }
}
