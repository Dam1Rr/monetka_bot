package com.monetka.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "telegram.bot")
public class BotProperties {

    /** Telegram Bot token from @BotFather */
    private String token;

    /** Bot username (without @) */
    private String username;

    /** Public HTTPS URL of the app (e.g. https://monetka-bot.up.railway.app) */
    private String webhookUrl;

    /** Telegram IDs of admins who can approve users */
    private List<Long> adminIds = List.of();
}
