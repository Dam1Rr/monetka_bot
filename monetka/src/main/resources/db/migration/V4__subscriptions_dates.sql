-- Заменяем day_of_month на start_date + end_date
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS start_date DATE;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS end_date   DATE;

-- Заполняем start_date из created_at для существующих записей
UPDATE subscriptions SET start_date = DATE(created_at) WHERE start_date IS NULL;

-- Делаем start_date обязательным
ALTER TABLE subscriptions ALTER COLUMN start_date SET NOT NULL;

-- Убираем старое поле
ALTER TABLE subscriptions DROP COLUMN IF EXISTS day_of_month;