package com.monetka.bot;

import com.monetka.bot.handler.CallbackHandler;
import com.monetka.bot.handler.CommandHandler;
import com.monetka.bot.handler.MessageHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Central dispatcher — routes incoming Telegram updates to the right handler.
 *
 * Order:
 * 1. CallbackQuery (inline button press)
 * 2. Command message (/start, /help, ...)
 * 3. Text message (main menu buttons, free text)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateDispatcher {

    private final CommandHandler  commandHandler;
    private final MessageHandler  messageHandler;
    private final CallbackHandler callbackHandler;

    public void dispatch(Update update, MonetkaBot bot) {
        try {
            if (update.hasCallbackQuery()) {
                callbackHandler.handle(update.getCallbackQuery(), bot);

            } else if (update.hasMessage() && update.getMessage().hasText()) {
                String text = update.getMessage().getText();

                if (text.startsWith("/")) {
                    commandHandler.handle(update.getMessage(), bot);
                } else {
                    messageHandler.handle(update.getMessage(), bot);
                }
            }
        } catch (Exception e) {
            log.error("Error processing update {}: {}", update.getUpdateId(), e.getMessage(), e);
        }
    }
}
