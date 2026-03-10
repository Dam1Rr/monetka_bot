-- Track when user blocks/unblocks the bot
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS blocked_bot  BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS blocked_at   TIMESTAMP    NULL,
    ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMP    NULL;

-- Index for quick churn queries --
CREATE INDEX IF NOT EXISTS idx_users_blocked_bot ON users(blocked_bot);
CREATE INDEX IF NOT EXISTS idx_users_last_seen   ON users(last_seen_at);
