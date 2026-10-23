package com.daymate.bot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "salary_profiles")
public class SalaryProfileEntity {
    @Id
    private Long telegramUserId;

    @Column(nullable = false)
    private boolean acledaEmployee;

    @Column(nullable = false)
    private int payTimes;

    @Column(nullable = false)
    private int payDay1;

    private Integer payDay2;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlySalary;

    private Instant updatedAt;

    protected SalaryProfileEntity() {
    }

    public SalaryProfileEntity(Long telegramUserId, boolean acledaEmployee, int payTimes, int payDay1,
                               Integer payDay2, BigDecimal monthlySalary, Instant updatedAt) {
        this.telegramUserId = telegramUserId;
        this.acledaEmployee = acledaEmployee;
        this.payTimes = payTimes;
        this.payDay1 = payDay1;
        this.payDay2 = payDay2;
        this.monthlySalary = monthlySalary;
        this.updatedAt = updatedAt;
    }

    public Long getTelegramUserId() {
        return telegramUserId;
    }

    public boolean isAcledaEmployee() {
        return acledaEmployee;
    }

    public int getPayTimes() {
        return payTimes;
    }

    public int getPayDay1() {
        return payDay1;
    }

    public Integer getPayDay2() {
        return payDay2;
    }

    public BigDecimal getMonthlySalary() {
        return monthlySalary;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
