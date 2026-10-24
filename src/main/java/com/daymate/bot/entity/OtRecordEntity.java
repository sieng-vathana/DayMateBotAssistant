package com.daymate.bot.entity;

import com.daymate.bot.domain.OtShift;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "ot_records")
public class OtRecordEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long telegramUserId;

    @Column(nullable = false)
    private Long chatId;

    @Column(nullable = false)
    private int recordNumber;

    @Column(nullable = false)
    private LocalDate workDate;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(nullable = false, length = 16)
    private String shift;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal grossHours;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal breakHours;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal paidHours;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal hourlyRate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    private Instant createdAt;

    protected OtRecordEntity() {
    }

    public OtRecordEntity(Long telegramUserId, Long chatId, int recordNumber, LocalDate workDate,
                          LocalTime startTime, LocalTime endTime, OtShift shift, BigDecimal grossHours,
                          BigDecimal breakHours, BigDecimal paidHours, BigDecimal hourlyRate,
                          BigDecimal amount, Instant createdAt) {
        this.telegramUserId = telegramUserId;
        this.chatId = chatId;
        this.recordNumber = recordNumber;
        this.workDate = workDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.shift = shift.name();
        this.grossHours = grossHours;
        this.breakHours = breakHours;
        this.paidHours = paidHours;
        this.hourlyRate = hourlyRate;
        this.amount = amount;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public int getRecordNumber() {
        return recordNumber;
    }

    public void setRecordNumber(int recordNumber) {
        this.recordNumber = recordNumber;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public OtShift getShift() {
        return OtShift.valueOf(shift);
    }

    public BigDecimal getGrossHours() {
        return grossHours;
    }

    public BigDecimal getBreakHours() {
        return breakHours;
    }

    public BigDecimal getPaidHours() {
        return paidHours;
    }

    public BigDecimal getHourlyRate() {
        return hourlyRate;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
