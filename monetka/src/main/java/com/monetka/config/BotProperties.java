package com.monetka.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "telegram.bot")
public class BotProperties {

    private String token;
    private String username;
    private String webhookUrl;
    private List<Long> adminIds = new ArrayList<>();

    public String getToken()            { return token; }
    public String getUsername()         { return username; }
    public String getWebhookUrl()       { return webhookUrl; }
    public List<Long> getAdminIds()     { return adminIds; }

    public void setToken(String token)              { this.token = token; }
    public void setUsername(String username)        { this.username = username; }
    public void setWebhookUrl(String webhookUrl)    { this.webhookUrl = webhookUrl; }
    public void setAdminIds(List<Long> adminIds)    { this.adminIds = adminIds; }
}