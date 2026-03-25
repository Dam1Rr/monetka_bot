-- ============================================================
-- Composite indexes for most frequent query patterns.
-- All report queries filter by (user_id, created_at, type).
-- ============================================================

-- Main query: user + time range + type (every report and daily summary)
CREATE INDEX IF NOT EXISTS idx_transactions_user_date_type
    ON transactions(user_id, created_at, type);

-- Category drill-down: user + category + time range (budget goals, overview)
CREATE INDEX IF NOT EXISTS idx_transactions_user_category_date
    ON transactions(user_id, category_id, created_at)
    WHERE type = 'EXPENSE';

-- NOTE: learned_keywords already has UNIQUE(keyword, user_id) which serves as index.
