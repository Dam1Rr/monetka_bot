package com.monetka.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookRegistrar implements ApplicationRunner {

    private final BotProperties botProperties;
    private final RestTemplate  restTemplate;

    private static final String TELEGRAM_API = "https://api.telegram.org/bot";
    private static final String WEBHOOK_PATH = "/webhook";

    @Override
    public void run(ApplicationArguments args) {
        String webhookTarget = botProperties.getWebhookUrl() + WEBHOOK_PATH;
        String apiUrl = TELEGRAM_API + botProperties.getToken() + "/setWebhook";

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                apiUrl + "?url={url}",
                null,
                Map.class,
                Map.of("url", webhookTarget)
            );

            if (Boolean.TRUE.equals(response.getBody() != null
                    ? response.getBody().get("ok") : false)) {
                log.info("✅ Webhook registered: {}", webhookTarget);
            } else {
                log.warn("⚠️  Webhook response: {}", response.getBody());
            }
        } catch (Exception e) {
            log.error("❌ Failed to register webhook: {}", e.getMessage());
        }
    }
}
