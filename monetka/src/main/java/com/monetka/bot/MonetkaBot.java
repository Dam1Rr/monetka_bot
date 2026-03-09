package com.monetka.bot;

import com.monetka.config.BotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramWebhookBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.ByteArrayInputStream;

@Component
public class MonetkaBot extends TelegramWebhookBot {

    private static final Logger logger = LoggerFactory.getLogger(MonetkaBot.class);

    private final BotProperties    botProperties;
    private final UpdateDispatcher dispatcher;

    public MonetkaBot(BotProperties botProperties, UpdateDispatcher dispatcher) {
        super(botProperties.getToken());
        this.botProperties = botProperties;
        this.dispatcher    = dispatcher;
    }

    @Override
    public BotApiMethod<?> onWebhookUpdateReceived(Update update) {
        dispatcher.dispatch(update, this);
        return null;
    }

    @Override public String getBotPath()     { return "/webhook"; }
    @Override public String getBotUsername() { return botProperties.getUsername(); }

    public void sendText(long chatId, String text) {
        sendMessage(chatId, text, null);
    }

    public void sendMarkdown(long chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);
        msg.setText(text);
        msg.setParseMode("Markdown");
        msg.setReplyMarkup(keyboard);
        try { execute(msg); } catch (TelegramApiException e) { logger.error("Ошибка отправки сообщения", e); }
    }

    public void sendMarkdown(long chatId, String text) {
        doExecute(SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text)
                .parseMode("Markdown")
                .build());
    }

    public void sendMessage(long chatId, String text, ReplyKeyboard keyboard) {
        SendMessage.SendMessageBuilder builder = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text)
                .parseMode("Markdown");
        if (keyboard != null) builder.replyMarkup(keyboard);
        doExecute(builder.build());
    }

    /**
     * Send a file (e.g. XLSX) as a document to a chat.
     * @param chatId   recipient
     * @param data     raw file bytes
     * @param filename filename shown in Telegram (e.g. "users.xlsx")
     * @param caption  optional caption text (Markdown)
     */
    public void sendDocument(long chatId, byte[] data, String filename, String caption) {
        try {
            SendDocument doc = SendDocument.builder()
                    .chatId(String.valueOf(chatId))
                    .document(new InputFile(new ByteArrayInputStream(data), filename))
                    .caption(caption != null ? caption : "")
                    .parseMode("Markdown")
                    .build();
            execute(doc);
        } catch (TelegramApiException e) {
            logger.error("Failed to send document to {}: {}", chatId, e.getMessage());
        }
    }

    public void editMessage(long chatId, int messageId, String text, InlineKeyboardMarkup keyboard) {
        try {
            EditMessageText.EditMessageTextBuilder b = EditMessageText.builder()
                    .chatId(String.valueOf(chatId))
                    .messageId(messageId)
                    .text(text)
                    .parseMode("Markdown");
            if (keyboard != null) b.replyMarkup(keyboard);
            execute(b.build());
        } catch (TelegramApiException e) {
            logger.warn("editMessage failed: {}", e.getMessage());
        }
    }

    private void doExecute(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Failed to send message to {}: {}", message.getChatId(), e.getMessage());
        }
    }
}