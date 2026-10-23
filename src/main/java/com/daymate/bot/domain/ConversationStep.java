package com.daymate.bot.domain;

public enum ConversationStep {
    TITLE,
    CATEGORY,
    PRIORITY,
    DUE,
    RECURRENCE,
    ACLEDA,
    WORK_DAYS,
    PAY_TIMES,
    PAY_DAY,
    PAY_DAY_2,
    BASE_SALARY,
    OT_TIME,
    OT_TYPE;

    public boolean isOtSetup() {
        return switch (this) {
            case ACLEDA, WORK_DAYS, PAY_TIMES, PAY_DAY, PAY_DAY_2, BASE_SALARY, OT_TIME, OT_TYPE -> true;
            default -> false;
        };
    }
}
