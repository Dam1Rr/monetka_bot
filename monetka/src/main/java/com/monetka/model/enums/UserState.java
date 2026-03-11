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
    WAITING_BROADCAST_MESSAGE,

    // Онбординг — начальный баланс
    WAITING_INITIAL_BALANCE,

    // Создание долга (шаг за шагом)
    WAITING_DEBT_NAME,
    WAITING_DEBT_TRIGGER,
    WAITING_DEBT_TOTAL,
    WAITING_DEBT_MONTHLY,
    WAITING_DEBT_PAID
}