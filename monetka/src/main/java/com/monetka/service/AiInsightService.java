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
import java.util.List;

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

    public String generateWeekInsight(
            List<CategoryStats> cats,
            BigDecimal total,
            BigDecimal prevTotal,
            java.time.LocalDate peakDay,
            BigDecimal peakAmt,
            BigDecimal avgPerDay,
            List<Transaction> txs
    ) {
        if (apiKey == null || apiKey.isBlank()) return null;
        try {
            StringBuilder prompt = new StringBuilder();
            prompt.append("Ты — Monetka, финансовый помощник в Telegram для Бишкека (Кыргызстан).\n");
            prompt.append("Валюта: сом. Пиши на русском, коротко и по делу, как умный друг.\n");
            prompt.append("Telegram Markdown: *жирный*. Без заголовков #. Максимум 3 строки.\n\n");
            prompt.append("ДАННЫЕ ЗА НЕДЕЛЮ:\n");
            prompt.append("- Потрачено: ").append(total).append(" сом\n");
            if (prevTotal.compareTo(BigDecimal.ZERO) > 0)
                prompt.append("- Прошлая неделя: ").append(prevTotal).append(" сом\n");
            prompt.append("- Среднее/день: ").append(avgPerDay).append(" сом\n");
            if (peakDay != null && peakAmt.compareTo(BigDecimal.ZERO) > 0)
                prompt.append("- Пиковый день: ").append(peakDay).append(" = ").append(peakAmt).append(" сом\n");
            if (!cats.isEmpty()) {
                prompt.append("- Категории:\n");
                cats.stream().limit(4).forEach(c ->
                        prompt.append("  ").append(c.label).append(": ").append(c.total).append(" (").append(c.percent).append("%)\n"));
            }
            // Top descriptions
            java.util.Map<String, BigDecimal> descMap = new java.util.LinkedHashMap<>();
            for (Transaction tx : txs) {
                String d = tx.getDescription() == null ? "?" : tx.getDescription();
                descMap.merge(d, tx.getAmount(), BigDecimal::add);
            }
            descMap.entrySet().stream()
                    .sorted((a,b) -> b.getValue().compareTo(a.getValue()))
                    .limit(4)
                    .forEach(e -> prompt.append("  • ").append(e.getKey()).append(": ").append(e.getValue()).append("\n"));

            prompt.append("\nЗАДАЧА: 1-2 предложения. Замечай что необычное или важное. ");
            prompt.append("Конкретная цифра обязательна. Не банальщина.\n");

            return callClaude(prompt.toString());
        } catch (Exception e) {
            log.warn("AI week insight failed: {}", e.getMessage());
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
            LocalDate d = tx.getCreatedAt().toLocalDate();
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


    // ================================================================
    // AI категоризация расходов
    // ================================================================

    /**
     * Определяет категорию расхода через Claude Haiku.
     * Возвращает AiCategory или null если API недоступен / не уверен.
     * SAFE: любая ошибка → тихо возвращает null, категоризация падает в keyword-based.
     */
    public AiCategory detectCategory(String expenseText, List<String> availableCategories) {
        return detectCategory(expenseText, availableCategories, null);
    }

    public AiCategory detectCategory(String expenseText, List<String> availableCategories,
                                     java.util.Map<String, List<String>> subcategoriesMap) {
        if (apiKey == null || apiKey.isBlank()) return null;
        if (expenseText == null || expenseText.isBlank()) return null;
        try {
            String categoriesList = String.join(", ", availableCategories);

            // Строим блок подкатегорий если есть
            StringBuilder subcatBlock = new StringBuilder();
            if (subcategoriesMap != null && !subcategoriesMap.isEmpty()) {
                subcatBlock.append("\nПодкатегории по категориям:\n");
                subcategoriesMap.forEach((cat, subcats) -> {
                    if (!subcats.isEmpty()) {
                        subcatBlock.append("  ").append(cat).append(": ")
                                .append(String.join(", ", subcats)).append("\n");
                    }
                });
            }

            String prompt = "Ты помощник по учёту расходов в Кыргызстане (валюта: сом).\n" +
                    "Определи категорию для расхода пользователя.\n" +
                    "Доступные категории: " + categoriesList + "\n" +
                    subcatBlock +
                    "\nОтветь СТРОГО в формате JSON (без markdown, без пояснений):\n" +
                    "{\"category\":\"...\"," +
                    "\"subcategory\":\"...\"," +
                    "\"confidence\":0.0}\n\n" +
                    "Расход: \"" + expenseText + "\"\n\n" +
                    "Правила:\n" +
                    "- confidence от 0.0 до 1.0 (насколько уверен)\n" +
                    "- Если не уверен — ставь confidence 0.3 и ниже\n" +
                    "- subcategory — точное название подкатегории или пустая строка если не подходит\n" +
                    "- Только JSON, ничего больше";

            String raw = callClaudeWithSystem(
                    "Ты отвечаешь ТОЛЬКО в формате JSON. Никакого текста кроме JSON.",
                    prompt,
                    120
            );
            if (raw == null || raw.isBlank()) return null;
            return parseAiCategory(raw);
        } catch (Exception e) {
            log.warn("AI category detection failed for '{}': {}", expenseText, e.getMessage());
            return null;
        }
    }

    private AiCategory parseAiCategory(String json) {
        try {
            String clean = json.trim()
                    .replaceAll("```json", "").replaceAll("```", "").trim();
            String cat    = extractJsonField(clean, "category");
            String subcat = extractJsonField(clean, "subcategory");
            String confStr = extractJsonField(clean, "confidence");
            if (cat == null || cat.isBlank()) return null;
            double conf = 0.5;
            try { conf = Double.parseDouble(confStr); } catch (Exception ignored) {}
            String subcatClean = (subcat != null && !subcat.isBlank()) ? subcat.trim() : null;
            return new AiCategory(cat.trim(), subcatClean, conf);
        } catch (Exception e) {
            log.warn("Failed to parse AI category JSON: {}", json);
            return null;
        }
    }

    private String extractJsonField(String json, String field) {
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int colon = json.indexOf(":", idx + key.length());
        if (colon < 0) return null;
        int start = colon + 1;
        // skip whitespace
        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (start >= json.length()) return null;
        if (json.charAt(start) == '"') {
            int end = json.indexOf('"', start + 1);
            return end < 0 ? null : json.substring(start + 1, end);
        }
        // number or boolean
        int end = start;
        while (end < json.length() && ",}\n ".indexOf(json.charAt(end)) < 0) end++;
        return json.substring(start, end).trim();
    }

    /** Вызов с system-промптом и кастомным max_tokens */
    private String callClaudeWithSystem(String system, String userMsg, int maxTokens) throws Exception {
        String body = "{"
                + "\"model\":\"claude-haiku-4-5-20251001\","
                + "\"max_tokens\":" + maxTokens + ","
                + "\"system\":" + jsonString(system) + ","
                + "\"messages\":[{\"role\":\"user\",\"content\":" + jsonString(userMsg) + "}]"
                + "}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            log.warn("Claude API returned {}", resp.statusCode());
            return null;
        }
        return extractText(resp.body());
    }

    public static class AiCategory {
        public final String category;
        public final String subcategory; // может быть null или пустой
        public final double confidence;
        public AiCategory(String category, String subcategory, double confidence) {
            this.category    = category;
            this.subcategory = subcategory;
            this.confidence  = confidence;
        }
        public boolean isConfident() { return confidence >= 0.70; }
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