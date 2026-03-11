-- ============================================================
-- V15: Долги + категория Кредиты/Займы
-- ============================================================

-- 1. Таблица долгов
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

CREATE INDEX IF NOT EXISTS idx_debts_user ON debts(user_id);
CREATE INDEX IF NOT EXISTS idx_debts_trigger ON debts(trigger_word);

-- 2. Категория Кредиты/Займы
INSERT INTO categories (name, emoji, is_default)
VALUES ('Кредиты/Займы', '💳', FALSE)
    ON CONFLICT (name) DO NOTHING;

-- 3. Подкатегории
INSERT INTO subcategories (category_id, name, emoji, keywords)
SELECT c.id, 'Кредит', '🏦', ARRAY['кредит','кредита','кредиту','займ','займа','займу','долг']
FROM categories c WHERE c.name = 'Кредиты/Займы'
ON CONFLICT DO NOTHING;

INSERT INTO subcategories (category_id, name, emoji, keywords)
SELECT c.id, 'Рассрочка', '📱', ARRAY['рассрочка','рассрочку','рассрочке','installment']
FROM categories c WHERE c.name = 'Кредиты/Займы'
ON CONFLICT DO NOTHING;

INSERT INTO subcategories (category_id, name, emoji, keywords)
SELECT c.id, 'Микрозайм', '⚡', ARRAY['мкк','микрокредит','микрозайм','mikro']
FROM categories c WHERE c.name = 'Кредиты/Займы'
ON CONFLICT DO NOTHING;