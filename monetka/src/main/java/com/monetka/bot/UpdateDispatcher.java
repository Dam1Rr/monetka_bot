package com.monetka.bot;

import com.monetka.bot.handler.CallbackHandler;
import com.monetka.bot.handler.CommandHandler;
import com.monetka.bot.handler.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class UpdateDispatcher {

    private static final Logger log = LoggerFactory.getLogger(UpdateDispatcher.class);

    private final CommandHandler  commandHandler;
    private final MessageHandler  messageHandler;
    private final CallbackHandler callbackHandler;

    public UpdateDispatcher(CommandHandler commandHandler,
                            MessageHandler messageHandler,
                            CallbackHandler callbackHandler) {
        this.commandHandler  = commandHandler;
        this.messageHandler  = messageHandler;
        this.callbackHandler = callbackHandler;
    }

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