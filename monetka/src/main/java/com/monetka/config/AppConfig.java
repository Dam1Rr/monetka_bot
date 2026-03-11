package com.monetka.config;

import com.monetka.service.AiInsightService;
import com.monetka.service.CategoryDetectionService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import javax.sql.DataSource;

@Configuration
public class AppConfig {

    @Autowired
    private CategoryDetectionService categoryDetectionService;

    @Autowired
    private AiInsightService aiInsightService;

    /** Wire AI после инициализации всех бинов — избегаем circular dependency */
    @PostConstruct
    public void wireAi() {
        categoryDetectionService.setAiInsightService(aiInsightService);
    }


    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public TelegramBotsApi telegramBotsApi() throws TelegramApiException {
        return new TelegramBotsApi(DefaultBotSession.class);
    }
}