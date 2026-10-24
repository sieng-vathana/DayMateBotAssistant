package com.daymate.bot.controller;

import com.daymate.bot.DayMateBotApplication;
import com.daymate.bot.entity.FeatureSettingEntity;
import com.daymate.bot.entity.ModuleSettingEntity;
import com.daymate.bot.entity.UserFeatureAccessEntity;
import com.daymate.bot.entity.UserModuleAccessEntity;
import com.daymate.bot.repository.FeatureSettingRepository;
import com.daymate.bot.repository.ModuleSettingRepository;
import com.daymate.bot.repository.UserFeatureAccessRepository;
import com.daymate.bot.repository.UserModuleAccessRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = DayMateBotApplication.class)
@AutoConfigureMockMvc
@Transactional
class TaskControllerTest {
    private static final String TASK_JSON = """
            {
              "telegramUserId": 42,
              "chatId": 42,
              "title": "Buy groceries",
              "category": "PERSONAL",
              "priority": "MEDIUM",
              "dueTime": "5:00 PM",
              "recurrence": "NONE"
            }
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ModuleSettingRepository modules;
    @Autowired
    private FeatureSettingRepository features;
    @Autowired
    private UserModuleAccessRepository userModules;
    @Autowired
    private UserFeatureAccessRepository userFeatures;

    @Test
    void healthIsUp() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void tasksAreDeniedUntilAccessIsGranted() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TASK_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("That action is currently unavailable."));
    }

    @Test
    void createAndFetchTodayAgenda() throws Exception {
        allowTasksForUser42();
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TASK_JSON))
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

    private void allowTasksForUser42() {
        Instant now = Instant.parse("2026-10-24T02:00:00Z");
        ModuleSettingEntity module = modules.findById("tasks").orElseThrow();
        module.setEnabled(true);
        modules.save(module);
        userModules.save(new UserModuleAccessEntity(42L, "tasks", true, now));
        for (String key : List.of("tasks.create", "tasks.today", "tasks.complete")) {
            FeatureSettingEntity feature = features.findById(key).orElseThrow();
            feature.setEnabled(true);
            features.save(feature);
            userFeatures.save(new UserFeatureAccessEntity(42L, key, true, now));
        }
    }
}
