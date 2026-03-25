package com.monetka.bot;

import com.monetka.config.BotProperties;
import com.monetka.model.User;
import com.monetka.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
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

import com.monetka.util.AppConstants;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class MonetkaBot extends TelegramWebhookBot {

    private static final Logger logger = LoggerFactory.getLogger(MonetkaBot.class);

    private final BotProperties  botProperties;
    private final UpdateDispatcher dispatcher;
    private final UserRepository userRepository;

    public MonetkaBot(BotProperties botProperties, UpdateDispatcher dispatcher,
                      @Lazy UserRepository userRepository) {
        super(botProperties.getToken());
        this.botProperties  = botProperties;
        this.dispatcher     = dispatcher;
        this.userRepository = userRepository;
    }

    @Override
    public BotApiMethod<?> onWebhookUpdateReceived(Update update) {
        dispatcher.dispatch(update, this);
        return null;
    }

    @Override public String getBotPath()     { return "/webhook"; }
    @Override public String getBotUsername() { return botProperties.getUsername(); }

    // ── Markdown safety ──────────────────────────────────────────────
    /** Escape user-provided text so Telegram Markdown doesn't break */
    public static String esc(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("_", "\\_")
                .replace("*", "\\*")
                .replace("[", "\\[")
                .replace("`", "\\`");
    }

    // ── Send helpers ──────────────────────────────────────────────────

    public void sendText(long chatId, String text) {
        sendMessage(chatId, text, null);
    }

    public void sendMarkdown(long chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);
        msg.setText(text);
        msg.setParseMode("Markdown");
        msg.setReplyMarkup(keyboard);
        doExecute(chatId, msg);
    }

    public void sendMarkdown(long chatId, String text) {
        doExecute(chatId, SendMessage.builder()
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
        doExecute(chatId, builder.build());
    }

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

    // ── Core executor with churn detection ───────────────────────────

    private void doExecute(long chatId, SendMessage message) {
        try {
            execute(message);
            markSeen(chatId);
        } catch (TelegramApiException e) {
            handleSendError(chatId, e);
        }
    }

    private void handleSendError(long chatId, TelegramApiException e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("403") || msg.contains("bot was blocked") || msg.contains("user is deactivated")) {
            logger.info("User {} blocked the bot — marking as churned", chatId);
            markChurned(chatId);
        } else {
            logger.error("Failed to send to {}: {}", chatId, msg);
        }
    }

    private void markChurned(long chatId) {
        try {
            userRepository.findByTelegramId(chatId).ifPresent(u -> {
                if (!u.isBlockedBot()) {
                    u.setBlockedBot(true);
                    u.setBlockedAt(LocalDateTime.now(AppConstants.BISHKEK));
                    userRepository.save(u);
                }
            });
        } catch (Exception ex) {
            logger.warn("Could not mark {} as churned: {}", chatId, ex.getMessage());
        }
    }

    public void markSeen(long chatId) {
        try {
            userRepository.findByTelegramId(chatId).ifPresent(u -> {
                LocalDateTime now = LocalDateTime.now(AppConstants.BISHKEK);
                boolean stale = u.getLastSeenAt() == null ||
                        u.getLastSeenAt().plusHours(1).isBefore(now);
                if (stale) {
                    if (u.isBlockedBot()) { u.setBlockedBot(false); u.setBlockedAt(null); }
                    u.setLastSeenAt(now);
                    userRepository.save(u);
                }
            });
        } catch (Exception ex) {
            logger.warn("Could not update last_seen for {}: {}", chatId, ex.getMessage());
        }
    }
}