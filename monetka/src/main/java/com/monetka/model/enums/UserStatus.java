package com.monetka.model.enums;

public enum UserStatus {
    /** Зарегистрирован, ожидает одобрения администратора */
    PENDING,

    /** Активный пользователь с полным доступом */
    ACTIVE,

    /** Заблокирован администратором */
    BLOCKED
}