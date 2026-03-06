-- ============================================================
-- V7: Budget goals (цели/лимиты по категориям)
-- ============================================================

CREATE TABLE IF NOT EXISTS budget_goals (
                                            id          BIGSERIAL PRIMARY KEY,
                                            user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    amount      NUMERIC(12,2) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, category_id)
    );

CREATE INDEX IF NOT EXISTS idx_budget_goals_user ON budget_goals(user_id);