package com.monetka.model.enums;

public enum UserState {
    IDLE,

    // Транзакции
    WAITING_EXPENSE,
    WAITING_INCOME,

    // Выбор категории вручную
    WAITING_CATEGORY_CHOICE,
    WAITING_SUBCATEGORY_CHOICE,

    // Подписки
    WAITING_SUB_NAME,
    WAITING_SUB_AMOUNT,
    WAITING_SUB_START_DATE,
    WAITING_SUB_END_DATE,

    // Бюджетные цели
    WAITING_GOAL_AMOUNT,

    // Редактирование транзакций
    WAITING_EDIT_AMOUNT,
    WAITING_EDIT_DESCRIPTION,

    // Admin
    WAITING_BROADCAST_MESSAGE
}