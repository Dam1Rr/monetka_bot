-- Bot-wide settings stored in DB (survives restarts, no env var changes needed)
CREATE TABLE IF NOT EXISTS bot_settings (
                                            key   VARCHAR(100) PRIMARY KEY,
    value VARCHAR(500) NOT NULL
    );

-- Default: open registration ON
INSERT INTO bot_settings (key, value) VALUES ('registration_open', 'true')
    ON CONFLICT (key) DO NOTHING;