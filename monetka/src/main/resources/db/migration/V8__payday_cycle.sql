-- ============================================================
-- V8: Payday cycle — tracks spending pace between paydays
-- ============================================================

CREATE TABLE IF NOT EXISTS payday_cycles (
                                             id          BIGSERIAL PRIMARY KEY,
                                             user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    start_date  DATE NOT NULL,
    total_income NUMERIC(12,2) NOT NULL DEFAULT 0,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_payday_cycles_user_active
    ON payday_cycles(user_id, is_active);