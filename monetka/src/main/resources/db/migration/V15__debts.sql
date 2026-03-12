-- V15: Debts table
CREATE TABLE IF NOT EXISTS debts (
                                     id              BIGSERIAL PRIMARY KEY,
                                     user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name            VARCHAR(200) NOT NULL,
    trigger_word    VARCHAR(100) NOT NULL,
    total_amount    NUMERIC(14,2) NOT NULL,
    remaining       NUMERIC(14,2) NOT NULL,
    monthly_payment NUMERIC(14,2) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    closed_at       TIMESTAMP
    );

CREATE INDEX IF NOT EXISTS idx_debts_user    ON debts(user_id);
CREATE INDEX IF NOT EXISTS idx_debts_trigger ON debts(trigger_word);