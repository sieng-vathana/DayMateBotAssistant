package com.daymate.bot.domain;

public enum TaskStatus {
    PENDING("Pending"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed");

    private final String label;

    TaskStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
