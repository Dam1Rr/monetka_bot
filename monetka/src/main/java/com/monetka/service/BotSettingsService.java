package com.monetka.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Stores bot-wide settings in DB.
 * Currently: registration mode (open / invite-only).
 * Easily extensible for future settings.
 */
@Service
public class BotSettingsService {

    private final JdbcTemplate jdbc;

    public BotSettingsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isRegistrationOpen() {
        String val = jdbc.queryForObject(
                "SELECT value FROM bot_settings WHERE key = 'registration_open'",
                String.class);
        return "true".equals(val);
    }

    public void setRegistrationOpen(boolean open) {
        jdbc.update(
                "INSERT INTO bot_settings (key, value) VALUES ('registration_open', ?) " +
                        "ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value",
                open ? "true" : "false");
    }
}