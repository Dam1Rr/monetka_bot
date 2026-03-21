-- V23: Streak system
-- streak_days  — текущая серия дней подряд
-- last_activity_date — дата последней транзакции (для проверки пропуска)
-- max_streak_days — рекорд пользователя (не сбрасывается)

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS streak_days       INT  NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_activity_date DATE,
    ADD COLUMN IF NOT EXISTS max_streak_days   INT  NOT NULL DEFAULT 0;