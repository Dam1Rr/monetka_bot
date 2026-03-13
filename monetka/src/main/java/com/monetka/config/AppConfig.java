package com.monetka.config;

import com.monetka.service.AiInsightService;
import com.monetka.service.CategoryDetectionService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;

@Configuration
public class AppConfig {

    @Autowired
    private CategoryDetectionService categoryDetectionService;

    @Autowired
    private AiInsightService aiInsightService;

    /**
     * Wire AI after all beans are initialized — avoids circular dependency.
     * CategoryDetectionService <- MessageHandler <- AiInsightService (if constructor).
     */
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
}