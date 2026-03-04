-- ============================================================
-- Monetka Bot — Initial Schema
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
    id          BIGSERIAL PRIMARY KEY,
    telegram_id BIGINT UNIQUE NOT NULL,
    username    VARCHAR(255),
    first_name  VARCHAR(255),
    last_name   VARCHAR(255),
    status      VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    balance     NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP
);

CREATE INDEX idx_users_telegram_id ON users(telegram_id);
CREATE INDEX idx_users_status       ON users(status);

-- ============================================================

CREATE TABLE IF NOT EXISTS categories (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) UNIQUE NOT NULL,
    emoji      VARCHAR(10),
    is_default BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS category_keywords (
    category_id BIGINT NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    keyword     VARCHAR(100) NOT NULL,
    PRIMARY KEY (category_id, keyword)
);

-- ============================================================

CREATE TABLE IF NOT EXISTS transactions (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount      NUMERIC(15,2) NOT NULL,
    description VARCHAR(500),
    category_id BIGINT       REFERENCES categories(id),
    type        VARCHAR(20)  NOT NULL,   -- INCOME | EXPENSE
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transactions_user_id    ON transactions(user_id);
CREATE INDEX idx_transactions_created_at ON transactions(created_at);
CREATE INDEX idx_transactions_type       ON transactions(type);

-- ============================================================

CREATE TABLE IF NOT EXISTS subscriptions (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name         VARCHAR(255) NOT NULL,
    amount       NUMERIC(15,2) NOT NULL,
    category_id  BIGINT       REFERENCES categories(id),
    day_of_month INT          NOT NULL DEFAULT 1,  -- 1..28
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_subscriptions_user_id ON subscriptions(user_id);
CREATE INDEX idx_subscriptions_active  ON subscriptions(active);
