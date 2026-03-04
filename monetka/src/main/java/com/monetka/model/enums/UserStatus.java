package com.monetka.model.enums;

public enum UserStatus {
    /** Registered but waiting for admin approval */
    PENDING,

    /** Approved and can use the bot */
    ACTIVE,

    /** Banned by admin */
    BLOCKED
}
