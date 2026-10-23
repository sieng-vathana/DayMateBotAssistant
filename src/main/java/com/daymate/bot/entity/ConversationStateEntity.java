package com.daymate.bot.entity;

import com.daymate.bot.domain.ConversationStep;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "conversation_states")
public class ConversationStateEntity {
    @Id
    private Long chatId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 32)
    private String step;

    @Column(length = 256)
    private String title;

    @Column(length = 32)
    private String category;

    @Column(length = 32)
    private String priority;

    private LocalDate dueDate;
    private LocalTime dueTime;
    private Boolean acledaEmployee;
    private Integer payTimes;
    private Integer payDay1;
    private Integer payDay2;
    private Integer workDaysPerWeek;
    private Boolean resumeOt;
    @Column(length = 32)
    private String pendingRange;
    private Instant updatedAt;

    protected ConversationStateEntity() {
    }

    public ConversationStateEntity(Long chatId, Long userId, ConversationStep step, Instant updatedAt) {
        this.chatId = chatId;
        this.userId = userId;
        this.step = step.name();
        this.updatedAt = updatedAt;
    }

    public void setStep(ConversationStep step) {
        this.step = step.name();
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public void setDueTime(LocalTime dueTime) {
        this.dueTime = dueTime;
    }

    public void setAcledaEmployee(Boolean acledaEmployee) {
        this.acledaEmployee = acledaEmployee;
    }

    public void setPayTimes(Integer payTimes) {
        this.payTimes = payTimes;
    }

    public void setPayDay1(Integer payDay1) {
        this.payDay1 = payDay1;
    }

    public void setPayDay2(Integer payDay2) {
        this.payDay2 = payDay2;
    }

    public void setWorkDaysPerWeek(Integer workDaysPerWeek) {
        this.workDaysPerWeek = workDaysPerWeek;
    }

    public void setResumeOt(Boolean resumeOt) {
        this.resumeOt = resumeOt;
    }

    public void setPendingRange(String pendingRange) {
        this.pendingRange = pendingRange;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getChatId() {
        return chatId;
    }

    public Long getUserId() {
        return userId;
    }

    public ConversationStep getStep() {
        return ConversationStep.valueOf(step);
    }

    public String getTitle() {
        return title;
    }

    public String getCategory() {
        return category;
    }

    public String getPriority() {
        return priority;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LocalTime getDueTime() {
        return dueTime;
    }

    public Boolean getAcledaEmployee() {
        return acledaEmployee;
    }

    public Integer getPayTimes() {
        return payTimes;
    }

    public Integer getPayDay1() {
        return payDay1;
    }

    public Integer getPayDay2() {
        return payDay2;
    }

    public Integer getWorkDaysPerWeek() {
        return workDaysPerWeek;
    }

    public boolean isResumeOt() {
        return Boolean.TRUE.equals(resumeOt);
    }

    public String getPendingRange() {
        return pendingRange;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
