package com.monetka.bot;

import com.monetka.config.BotProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramWebhookBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Core bot class.
 * - Receives updates from WebhookController
 * - Provides helper methods to send messages
 * - Delegates update processing to UpdateDispatcher
 */
@Slf4j
@Component
public class MonetkaBot extends TelegramWebhookBot {

    private final BotProperties     botProperties;
    private final UpdateDispatcher  dispatcher;

    public MonetkaBot(BotProperties botProperties, UpdateDispatcher dispatcher) {
        super(botProperties.getToken());
        this.botProperties = botProperties;
        this.dispatcher    = dispatcher;
    }

    // ---- Webhook interface ----

    @Override
    public BotApiMethod<?> onWebhookUpdateReceived(Update update) {
        // Delegate to dispatcher; actual sends happen via execute() in handlers
        dispatcher.dispatch(update, this);
        return null;
    }

    @Override
    public String getBotPath() {
        return "/webhook";
    }

    @Override
    public String getBotUsername() {
        return botProperties.getUsername();
    }

    // ---- Sending helpers ----

    public void sendText(long chatId, String text) {
        sendMessage(chatId, text, null);
    }

    public void sendMarkdown(long chatId, String text) {
        SendMessage msg = SendMessage.builder()
            .chatId(String.valueOf(chatId))
            .text(text)
            .parseMode("Markdown")
            .build();
        doExecute(msg);
    }

    public void sendMessage(long chatId, String text, ReplyKeyboard keyboard) {
        SendMessage.SendMessageBuilder builder = SendMessage.builder()
            .chatId(String.valueOf(chatId))
            .text(text)
            .parseMode("Markdown");

        if (keyboard != null) {
            builder.replyMarkup(keyboard);
        }

        doExecute(builder.build());
    }

    private void doExecute(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send message to {}: {}",
                message.getChatId(), e.getMessage());
        }
    }
}
