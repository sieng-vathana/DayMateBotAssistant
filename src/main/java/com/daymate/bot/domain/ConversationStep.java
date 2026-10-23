package com.daymate.bot.domain;

public enum ConversationStep {
    TITLE,
    CATEGORY,
    PRIORITY,
    DUE,
    RECURRENCE,
    ACLEDA,
    PAY_TIMES,
    PAY_DAY,
    PAY_DAY_2,
    BASE_SALARY,
    OT_TIME;

    public boolean isOtSetup() {
        return switch (this) {
            case ACLEDA, PAY_TIMES, PAY_DAY, PAY_DAY_2, BASE_SALARY, OT_TIME -> true;
            default -> false;
        };
    }
}
