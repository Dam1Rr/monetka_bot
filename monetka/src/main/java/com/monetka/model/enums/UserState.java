package com.monetka.model.enums;

public enum UserState {
    IDLE,

    // --- Transactions ---
    WAITING_EXPENSE,
    WAITING_INCOME,

    // --- Subscription wizard ---
    WAITING_SUB_NAME,
    WAITING_SUB_AMOUNT,
    WAITING_SUB_START_DATE,
    WAITING_SUB_END_DATE
}