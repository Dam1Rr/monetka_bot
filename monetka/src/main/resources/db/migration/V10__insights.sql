CREATE TABLE user_insights (
                               id          BIGSERIAL PRIMARY KEY,
                               user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                               trigger_key VARCHAR(100) NOT NULL,
                               sent_at     TIMESTAMP NOT NULL DEFAULT NOW(),
                               month       INTEGER NOT NULL,
                               year        INTEGER NOT NULL
);

CREATE INDEX idx_user_insights_user_month ON user_insights(user_id, month, year);

CREATE TABLE user_monthly_profiles (
                                       id               BIGSERIAL PRIMARY KEY,
                                       user_id          BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                       month            INTEGER NOT NULL,
                                       year             INTEGER NOT NULL,
                                       personality_type VARCHAR(50),
                                       peak_day         VARCHAR(20),
                                       peak_hour        INTEGER,
                                       top_category     VARCHAR(100),
                                       savings_pct      INTEGER DEFAULT 0,
                                       night_pct        INTEGER DEFAULT 0,
                                       impulse_score    INTEGER DEFAULT 0,
                                       discipline_score INTEGER DEFAULT 0,
                                       created_at       TIMESTAMP DEFAULT NOW(),
                                       UNIQUE(user_id, month, year)
);