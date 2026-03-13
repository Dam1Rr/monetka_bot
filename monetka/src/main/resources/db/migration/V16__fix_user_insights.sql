-- V16: Fix user_insights schema mismatch
-- 1. Rename fired_at -> sent_at (if it exists)
-- 2. Add month + year columns
-- 3. Fix unique constraint
-- 4. Fix index

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name='user_insights'
        AND column_name='fired_at'
    ) THEN
ALTER TABLE user_insights
    RENAME COLUMN fired_at TO sent_at;
END IF;
END $$;

ALTER TABLE user_insights
    ADD COLUMN IF NOT EXISTS month SMALLINT,
    ADD COLUMN IF NOT EXISTS year  SMALLINT;

-- заполнить month/year из sent_at если они null
UPDATE user_insights
SET
    month = EXTRACT(MONTH FROM sent_at),
    year  = EXTRACT(YEAR  FROM sent_at)
WHERE month IS NULL OR year IS NULL;

ALTER TABLE user_insights
    ALTER COLUMN month SET NOT NULL,
ALTER COLUMN year  SET NOT NULL;

-- удалить старый constraint
ALTER TABLE user_insights
DROP CONSTRAINT IF EXISTS user_insights_user_id_trigger_key_fired_at_key;

-- новый constraint
ALTER TABLE user_insights
DROP CONSTRAINT IF EXISTS uq_user_insights_monthly;

ALTER TABLE user_insights
    ADD CONSTRAINT uq_user_insights_monthly
        UNIQUE (user_id, trigger_key, month, year);

-- индекс для быстрых запросов
DROP INDEX IF EXISTS idx_user_insights_trigger;

CREATE INDEX IF NOT EXISTS idx_user_insights_recent
    ON user_insights(user_id, sent_at DESC);