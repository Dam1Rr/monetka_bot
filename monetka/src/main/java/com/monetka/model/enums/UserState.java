package com.monetka.model.enums;

/**
 * FSM states stored in-memory (UserStateService).
 * Tracks what input the bot is currently expecting from a user.
 */
public enum UserState {
    IDLE,

    // --- Transactions ---
    WAITING_EXPENSE,
    WAITING_INCOME,

    // --- Subscription wizard ---
    WAITING_SUB_NAME,
    WAITING_SUB_AMOUNT,
    WAITING_SUB_DAY
}
