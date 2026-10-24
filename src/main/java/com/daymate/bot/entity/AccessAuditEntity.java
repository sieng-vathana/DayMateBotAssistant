package com.daymate.bot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "access_audit")
public class AccessAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String actor;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(nullable = false, length = 96)
    private String target;

    @Column(nullable = false)
    private Instant createdAt;

    protected AccessAuditEntity() {
    }

    public AccessAuditEntity(String actor, String action, String target, Instant createdAt) {
        this.actor = actor;
        this.action = action;
        this.target = target;
        this.createdAt = createdAt;
    }
}
