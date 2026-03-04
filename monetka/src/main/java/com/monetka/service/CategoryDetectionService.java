package com.monetka.service;

import com.monetka.model.Category;
import com.monetka.model.LearnedKeyword;
import com.monetka.model.Subcategory;
import com.monetka.repository.CategoryRepository;
import com.monetka.repository.LearnedKeywordRepository;
import com.monetka.repository.SubcategoryRepository;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Умная система определения категорий.
 *
 * Алгоритм (по убыванию приоритета):
 * 1. Персональные обученные слова пользователя
 * 2. Глобальные обученные слова
 * 3. Точное совпадение в словаре подкатегорий
 * 4. Fuzzy matching (расстояние Левенштейна ≤ 2)
 * 5. Подстрочное вхождение
 * 6. Категория "Прочее" по умолчанию
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryDetectionService {

    private final CategoryRepository      categoryRepository;
    private final SubcategoryRepository   subcategoryRepository;
    private final LearnedKeywordRepository learnedKeywordRepository;

    /** keyword → Subcategory (индекс для быстрого поиска) */
    private final Map<String, Subcategory> keywordIndex = new HashMap<>();
    private Category defaultCategory;

    // ================================================================
    // Инициализация индекса
    // ================================================================

    @PostConstruct
    public void buildIndex() {
        keywordIndex.clear();
        List<Category> all = categoryRepository.findAll();

        for (Category category : all) {
            if (category.isDefault()) defaultCategory = category;

            for (Subcategory sub : category.getSubcategories()) {
                for (String kw : sub.getKeywords()) {
                    keywordIndex.put(kw.toLowerCase().trim(), sub);
                }
            }
        }

        log.info("Category index: {} keywords, {} categories",
                keywordIndex.size(), all.size());
    }

    public void reload() {
        buildIndex();
    }

    // ================================================================
    // Основной метод определения категории
    // ================================================================

    /**
     * @param text       оригинальный текст ("шаурма 300")
     * @param userId     Telegram ID пользователя (для персональных слов)
     * @return DetectionResult с category, subcategory, confidence
     */
    @Transactional(readOnly = true)
    public DetectionResult detectCategory(String text, Long userId) {
        if (text == null || text.isBlank()) return defaultResult();

        // Нормализация: убираем числа, lower case, trim
        String normalized = normalize(text);
        if (normalized.isBlank()) return defaultResult();

        String[] tokens = normalized.split("\\s+");

        // --- Шаг 1: персональные обученные слова ---
        for (String token : tokens) {
            List<LearnedKeyword> learned =
                    learnedKeywordRepository.findByKeywordAndUser(token, userId);
            if (!learned.isEmpty()) {
                LearnedKeyword lk = learned.get(0);
                log.debug("Learned match '{}' → {}", token, lk.getCategory().getName());
                return new DetectionResult(lk.getCategory(), lk.getSubcategory(), 1.0, token);
            }
        }

        // --- Шаг 2: точное совпадение в словаре ---
        for (String token : tokens) {
            Subcategory sub = keywordIndex.get(token);
            if (sub != null) {
                log.debug("Exact match '{}' → {}/{}", token,
                        sub.getCategory().getName(), sub.getName());
                return new DetectionResult(sub.getCategory(), sub, 1.0, token);
            }
        }

        // --- Шаг 3: проверка полной фразы (например "burger king") ---
        for (int len = tokens.length; len >= 2; len--) {
            for (int i = 0; i <= tokens.length - len; i++) {
                String phrase = String.join(" ",
                        Arrays.copyOfRange(tokens, i, i + len));
                Subcategory sub = keywordIndex.get(phrase);
                if (sub != null) {
                    log.debug("Phrase match '{}' → {}/{}", phrase,
                            sub.getCategory().getName(), sub.getName());
                    return new DetectionResult(sub.getCategory(), sub, 1.0, phrase);
                }
            }
        }

        // --- Шаг 4: подстрочное вхождение ---
        for (String token : tokens) {
            for (Map.Entry<String, Subcategory> entry : keywordIndex.entrySet()) {
                if (token.contains(entry.getKey()) || entry.getKey().contains(token)) {
                    double conf = (double) Math.min(token.length(), entry.getKey().length())
                            / Math.max(token.length(), entry.getKey().length());
                    if (conf >= 0.75) {
                        Subcategory sub = entry.getValue();
                        log.debug("Substring match '{}' ~ '{}' conf={}", token, entry.getKey(), conf);
                        return new DetectionResult(sub.getCategory(), sub, conf, token);
                    }
                }
            }
        }

        // --- Шаг 5: fuzzy matching (Левенштейн ≤ 2) ---
        BestMatch bestFuzzy = findBestFuzzyMatch(tokens);
        if (bestFuzzy != null) {
            Subcategory sub = bestFuzzy.subcategory;
            log.debug("Fuzzy match '{}' → {}/{} conf={}",
                    bestFuzzy.token, sub.getCategory().getName(), sub.getName(), bestFuzzy.confidence);
            return new DetectionResult(sub.getCategory(), sub, bestFuzzy.confidence, bestFuzzy.token);
        }

        // --- Не нашли ---
        log.debug("No match for '{}'", normalized);
        return defaultResult();
    }

    // ================================================================
    // Самообучение
    // ================================================================

    /**
     * Сохраняет выбор пользователя — слово → категория/подкатегория.
     * При повторном использовании инкрементирует счётчик.
     */
    @Transactional
    public void learnKeyword(String word, Category category,
                             Subcategory subcategory, Long userId) {
        String kw = word.toLowerCase().trim();

        Optional<LearnedKeyword> existing =
                learnedKeywordRepository.findByKeywordAndUserId(kw, userId);

        if (existing.isPresent()) {
            LearnedKeyword lk = existing.get();
            lk.setCategory(category);
            lk.setSubcategory(subcategory);
            lk.setUseCount(lk.getUseCount() + 1);
            learnedKeywordRepository.save(lk);
        } else {
            learnedKeywordRepository.save(LearnedKeyword.builder()
                    .keyword(kw)
                    .category(category)
                    .subcategory(subcategory)
                    .userId(userId)
                    .useCount(1)
                    .build());
        }

        log.info("Learned: '{}' → {}/{} for user {}",
                kw, category.getName(),
                subcategory != null ? subcategory.getName() : "—",
                userId);
    }

    // ================================================================
    // Helpers
    // ================================================================

    /** Нормализация: нижний регистр, убрать числа и спецсимволы */
    public String normalize(String text) {
        return text.toLowerCase()
                .replaceAll("[0-9]+[.,]?[0-9]*", "") // убрать числа
                .replaceAll("[^а-яёa-z\\s-]", " ")   // только буквы
                .replaceAll("\\s+", " ")
                .trim();
    }

    private DetectionResult defaultResult() {
        return new DetectionResult(defaultCategory, null, 0.0, null);
    }

    /** Fuzzy поиск с расстоянием Левенштейна ≤ 2 */
    private BestMatch findBestFuzzyMatch(String[] tokens) {
        BestMatch best = null;

        for (String token : tokens) {
            if (token.length() < 3) continue; // слишком короткое слово

            for (Map.Entry<String, Subcategory> entry : keywordIndex.entrySet()) {
                String kw = entry.getKey();
                if (Math.abs(token.length() - kw.length()) > 2) continue;

                int dist = levenshtein(token, kw);
                if (dist <= 2) {
                    double conf = 1.0 - (dist / (double) Math.max(token.length(), kw.length()));
                    if (best == null || conf > best.confidence) {
                        best = new BestMatch(token, entry.getValue(), conf);
                    }
                }
            }
        }

        return (best != null && best.confidence >= 0.65) ? best : null;
    }

    /** Расстояние Левенштейна */
    private int levenshtein(String a, String b) {
        int la = a.length(), lb = b.length();
        int[][] dp = new int[la + 1][lb + 1];

        for (int i = 0; i <= la; i++) dp[i][0] = i;
        for (int j = 0; j <= lb; j++) dp[0][j] = j;

        for (int i = 1; i <= la; i++) {
            for (int j = 1; j <= lb; j++) {
                dp[i][j] = a.charAt(i - 1) == b.charAt(j - 1)
                        ? dp[i - 1][j - 1]
                        : 1 + Math.min(dp[i - 1][j - 1],
                        Math.min(dp[i - 1][j], dp[i][j - 1]));
            }
        }
        return dp[la][lb];
    }

    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    // ================================================================
    // Inner classes
    // ================================================================

    @Getter
    public static class DetectionResult {
        private final Category    category;
        private final Subcategory subcategory;
        private final double      confidence;
        private final String      matchedKeyword;

        public DetectionResult(Category cat, Subcategory sub,
                               double confidence, String matched) {
            this.category       = cat;
            this.subcategory    = sub;
            this.confidence     = confidence;
            this.matchedKeyword = matched;
        }

        public boolean isConfident() {
            return confidence >= 0.65;
        }

        public String display() {
            if (subcategory != null)
                return category.getDisplayName() + " → " + subcategory.getDisplayName();
            return category.getDisplayName();
        }
    }

    private static class BestMatch {
        final String      token;
        final Subcategory subcategory;
        final double      confidence;

        BestMatch(String t, Subcategory s, double c) {
            token = t; subcategory = s; confidence = c;
        }
    }
}