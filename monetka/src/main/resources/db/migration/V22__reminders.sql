-- ================================================================
-- V22: User reminder settings
-- Пользователь может включить напоминания и выбрать время
-- ================================================================

CREATE TABLE IF NOT EXISTS user_reminders (
                                              id              BIGSERIAL PRIMARY KEY,
                                              user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    enabled         BOOLEAN NOT NULL DEFAULT false,
    hour_morning    INT NOT NULL DEFAULT 13,   -- 13:00
    hour_evening    INT NOT NULL DEFAULT 21,   -- 21:00
    morning_enabled BOOLEAN NOT NULL DEFAULT true,
    evening_enabled BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE (user_id)
    );