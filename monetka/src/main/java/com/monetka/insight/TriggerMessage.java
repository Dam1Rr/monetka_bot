package com.monetka.insight;

/**
 * Один триггер: ключ + текст сообщения
 * key начинается с "neg_" если негативный, "pos_" если позитивный
 */
public record TriggerMessage(String key, String text) {
    public boolean isNegative() { return key.startsWith("neg_"); }
}