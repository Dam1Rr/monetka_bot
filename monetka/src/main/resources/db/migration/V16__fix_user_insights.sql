-- V16: Fix user_insights schema mismatch
--   1. Rename fired_at -> sent_at (matches UserInsight.java)
--   2. Add month + year columns (used for per-month dedup)
--   3. Replace broken unique constraint with correct one: (user_id, trigger_key, month, year)

ALTER TABLE user_insights RENAME COLUMN fired_at TO sent_at;

ALTER TABLE user_insights
    ADD COLUMN IF NOT EXISTS month SMALLINT NOT NULL DEFAULT EXTRACT(MONTH FROM sent_at)::SMALLINT,
    ADD COLUMN IF NOT EXISTS year  SMALLINT NOT NULL DEFAULT EXTRACT(YEAR  FROM sent_at)::SMALLINT;

-- Remove old useless unique constraint (on timestamp — never actually deduplicates)
ALTER TABLE user_insights DROP CONSTRAINT IF EXISTS user_insights_user_id_trigger_key_fired_at_key;

-- Add correct unique constraint: one insight per trigger per month per user
ALTER TABLE user_insights
    ADD CONSTRAINT uq_user_insights_monthly
        UNIQUE (user_id, trigger_key, month, year);

-- Update index on sent_at for countRecentInsights query
DROP INDEX IF EXISTS idx_user_insights_trigger;
CREATE INDEX IF NOT EXISTS idx_user_insights_recent
    ON user_insights(user_id, sent_at DESC);