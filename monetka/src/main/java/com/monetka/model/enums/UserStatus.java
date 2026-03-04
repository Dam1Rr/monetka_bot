package com.monetka.model.enums;

public enum UserStatus {
    /** Зарегистрирован, ожидает одобрения администратора */
    PENDING,

    /** Одобрен — имеет полный доступ */
    APPROVED,

    /** Заблокирован администратором */
    BLOCKED
}