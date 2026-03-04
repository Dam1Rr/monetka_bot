package com.monetka.controller;

import com.monetka.bot.MonetkaBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Receives webhook POST requests from Telegram.
 *
 * Telegram calls: POST https://your-app.railway.app/webhook
 * with a JSON body of type Update.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class WebhookController {

    private final MonetkaBot bot;

    @PostMapping("/webhook")
    public ResponseEntity<String> onUpdate(@RequestBody Update update) {
        log.debug("Update received: {}", update.getUpdateId());
        try {
            bot.onWebhookUpdateReceived(update);
        } catch (Exception e) {
            log.error("Error handling update: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Monetka bot is running 💰");
    }
}
