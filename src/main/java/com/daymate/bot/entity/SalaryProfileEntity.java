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

    private Integer workDaysPerWeek;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlySalary;

    private Instant updatedAt;

    protected SalaryProfileEntity() {
    }

    public SalaryProfileEntity(Long telegramUserId, boolean acledaEmployee, int payTimes, int payDay1,
                               Integer payDay2, int workDaysPerWeek, BigDecimal monthlySalary, Instant updatedAt) {
        this.telegramUserId = telegramUserId;
        this.acledaEmployee = acledaEmployee;
        this.payTimes = payTimes;
        this.payDay1 = payDay1;
        this.payDay2 = payDay2;
        this.workDaysPerWeek = workDaysPerWeek >= 6 ? 6 : 5;
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

    public int resolvedWorkDaysPerWeek() {
        if (workDaysPerWeek != null && workDaysPerWeek >= 6) {
            return 6;
        }
        if (workDaysPerWeek != null && workDaysPerWeek == 5) {
            return 5;
        }
        return acledaEmployee ? 5 : 6;
    }

    public BigDecimal getMonthlySalary() {
        return monthlySalary;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
