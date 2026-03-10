package com.monetka.service;

import com.monetka.model.Transaction;
import com.monetka.service.StatisticsService.CategoryStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * AI-powered financial insights via Claude API.
 * SAFE: если ключа нет или запрос упал — возвращает null,
 * ReportService автоматически использует шаблонный инсайт.
 */
@Service
public class AiInsightService {

    private static final Logger log = LoggerFactory.getLogger(AiInsightService.class);
    private static final ZoneId BISHKEK = ZoneId.of("Asia/Bishkek");

    @Value("${ANTHROPIC_API_KEY:}")
    private String apiKey;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Генерирует AI-инсайт для месячного отчёта.
     * @return текст инсайта или null если AI недоступен
     */
    public String generateMonthInsight(
            List<CategoryStats> cats,
            BigDecimal income,
            BigDecimal expenses,
            BigDecimal diff,
            List<Transaction> txs,
            int dayOfMonth,
            int daysInMonth,
            BigDecimal prevExpenses
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("ANTHROPIC_API_KEY not set, using template insight");
            return null;
        }

        try {
            String prompt = buildPrompt(cats, income, expenses, diff, txs, dayOfMonth, daysInMonth, prevExpenses);
            String response = callClaude(prompt);
            if (response == null || response.isBlank()) return null;
            return response.trim();
        } catch (Exception e) {
            log.warn("AI insight failed, falling back to template: {}", e.getMessage());
            return null;
        }
    }

    private String buildPrompt(
            List<CategoryStats> cats,
            BigDecimal income,
            BigDecimal expenses,
            BigDecimal diff,
            List<Transaction> txs,
            int dayOfMonth,
            int daysInMonth,
            BigDecimal prevExpenses
    ) {
        StringBuilder sb = new StringBuilder();

        // Считаем дневные траты для анализа аномалий
        Map<LocalDate, BigDecimal> byDay = new LinkedHashMap<>();
        Map<String, BigDecimal> descTotals = new LinkedHashMap<>();
        for (Transaction tx : txs) {
            LocalDate d = tx.getCreatedAt()
                    .atZone(java.time.ZoneOffset.UTC)
                    .withZoneSameInstant(BISHKEK)
                    .toLocalDate();
            byDay.merge(d, tx.getAmount(), BigDecimal::add);
            String desc = tx.getDescription() == null ? "unknown" : tx.getDescription().toLowerCase();
            descTotals.merge(desc, tx.getAmount(), BigDecimal::add);
        }

        // Находим максимальный день
        LocalDate peakDay = null;
        BigDecimal peakAmt = BigDecimal.ZERO;
        for (Map.Entry<LocalDate, BigDecimal> e : byDay.entrySet()) {
            if (e.getValue().compareTo(peakAmt) > 0) {
                peakAmt = e.getValue();
                peakDay = e.getKey();
            }
        }

        // Средний день без пика
        BigDecimal expensesWithoutPeak = expenses.subtract(peakAmt);
        int daysWithoutPeak = Math.max(1, dayOfMonth - 1);
        BigDecimal avgWithoutPeak = daysWithoutPeak > 0
                ? expensesWithoutPeak.divide(BigDecimal.valueOf(daysWithoutPeak), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        sb.append("Ты — Monetka, умный финансовый помощник в Telegram для пользователей из Бишкека (Кыргызстан).\n");
        sb.append("Валюта: сом (KGS). Пиши на русском, дружелюбно, как умный друг — не банк.\n");
        sb.append("Используй Telegram Markdown: *жирный*, _курсив_. Без заголовков #.\n\n");

        sb.append("ДАННЫЕ ПОЛЬЗОВАТЕЛЯ ЗА МЕСЯЦ:\n");
        sb.append("- Прошло дней: ").append(dayOfMonth).append(" из ").append(daysInMonth).append("\n");
        sb.append("- Доход: ").append(income).append(" сом\n");
        sb.append("- Расходы: ").append(expenses).append(" сом\n");
        sb.append("- Остаток: ").append(diff).append(" сом\n");

        if (prevExpenses.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("- Расходы прошлого месяца: ").append(prevExpenses).append(" сом\n");
        }

        if (!cats.isEmpty()) {
            sb.append("- Топ категорий:\n");
            for (CategoryStats c : cats) {
                sb.append("  • ").append(c.label).append(": ").append(c.total).append(" сом (").append(c.percent).append("%)\n");
            }
        }

        if (peakDay != null && peakAmt.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("- Самый дорогой день: ").append(peakDay).append(" = ").append(peakAmt).append(" сом\n");
            sb.append("- Средний день без пика: ").append(avgWithoutPeak).append(" сом\n");
        }

        // Топ-5 трат по описанию
        if (!descTotals.isEmpty()) {
            sb.append("- Топ трат по названию:\n");
            descTotals.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .limit(5)
                    .forEach(e -> sb.append("  • ").append(e.getKey()).append(": ").append(e.getValue()).append(" сом\n"));
        }

        sb.append("\nЗАДАЧА:\n");
        sb.append("Напиши 1 умный персональный инсайт — 3-5 строк максимум.\n");
        sb.append("Замечай паттерны, аномалии, неочевидные вещи.\n");
        sb.append("Дай 1 конкретный совет с реальными цифрами.\n");
        sb.append("НЕ пиши банальщину типа 'составляй список покупок'.\n");
        sb.append("НЕ начинай с 'Отличный месяц!' или похожего.\n");
        sb.append("Будь честным и точным.\n");

        return sb.toString();
    }

    private String callClaude(String prompt) throws Exception {
        String body = "{"
                + "\"model\":\"claude-haiku-4-5-20251001\","
                + "\"max_tokens\":300,"
                + "\"messages\":[{\"role\":\"user\",\"content\":"
                + jsonString(prompt)
                + "}]"
                + "}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() != 200) {
            log.warn("Claude API returned {}: {}", resp.statusCode(), resp.body());
            return null;
        }

        return extractText(resp.body());
    }

    /** Простой парсер без Jackson — не добавляем зависимости */
    private String extractText(String json) {
        // {"content":[{"type":"text","text":"..."}],...}
        int idx = json.indexOf("\"text\":");
        if (idx < 0) return null;
        int start = json.indexOf('"', idx + 7) + 1;
        int end = start;
        boolean escaped = false;
        StringBuilder sb = new StringBuilder();
        while (end < json.length()) {
            char c = json.charAt(end);
            if (escaped) {
                if (c == 'n') sb.append('\n');
                else if (c == 't') sb.append('\t');
                else if (c == '"') sb.append('"');
                else if (c == '\\') sb.append('\\');
                else sb.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
            end++;
        }
        return sb.toString();
    }

    private String jsonString(String s) {
        return "\"" + s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }
}