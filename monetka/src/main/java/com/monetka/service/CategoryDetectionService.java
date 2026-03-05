package com.monetka.service;

import com.monetka.model.Category;
import com.monetka.model.LearnedKeyword;
import com.monetka.model.Subcategory;
import com.monetka.repository.CategoryRepository;
import com.monetka.repository.LearnedKeywordRepository;
import com.monetka.repository.SubcategoryRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryDetectionService {

    private final CategoryRepository       categoryRepository;
    private final SubcategoryRepository    subcategoryRepository;
    private final LearnedKeywordRepository learnedKeywordRepository;

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
        log.info("Category index built: {} keywords across {} categories",
                keywordIndex.size(), all.size());
    }

    public void reload() { buildIndex(); }

    // ================================================================
    // Определение категории
    // ================================================================

    @Transactional(readOnly = true)
    public DetectionResult detectCategory(String text, Long userId) {
        if (text == null || text.isBlank()) return defaultResult();

        String normalized = normalize(text);
        if (normalized.isBlank()) return defaultResult();

        String[] tokens = normalized.split("\\s+");

        // 1. Персональные обученные слова
        for (String token : tokens) {
            List<LearnedKeyword> learned =
                    learnedKeywordRepository.findByKeywordAndUser(token, userId);
            if (!learned.isEmpty()) {
                LearnedKeyword lk = learned.get(0);
                return new DetectionResult(lk.getCategory(), lk.getSubcategory(), 1.0, token);
            }
        }

        // 2. Точное совпадение
        for (String token : tokens) {
            Subcategory sub = keywordIndex.get(token);
            if (sub != null) return new DetectionResult(sub.getCategory(), sub, 1.0, token);
        }

        // 3. Фраза (например "burger king")
        for (int len = tokens.length; len >= 2; len--) {
            for (int i = 0; i <= tokens.length - len; i++) {
                String phrase = String.join(" ", Arrays.copyOfRange(tokens, i, i + len));
                Subcategory sub = keywordIndex.get(phrase);
                if (sub != null) return new DetectionResult(sub.getCategory(), sub, 1.0, phrase);
            }
        }

        // 4. Подстрочное вхождение
        for (String token : tokens) {
            for (Map.Entry<String, Subcategory> entry : keywordIndex.entrySet()) {
                String kw = entry.getKey();
                if (token.contains(kw) || kw.contains(token)) {
                    double conf = (double) Math.min(token.length(), kw.length())
                            / Math.max(token.length(), kw.length());
                    if (conf >= 0.75) {
                        Subcategory sub = entry.getValue();
                        return new DetectionResult(sub.getCategory(), sub, conf, token);
                    }
                }
            }
        }

        // 5. Fuzzy matching (Левенштейн ≤ 2)
        BestMatch bestFuzzy = findBestFuzzyMatch(tokens);
        if (bestFuzzy != null) {
            Subcategory sub = bestFuzzy.subcategory;
            return new DetectionResult(sub.getCategory(), sub, bestFuzzy.confidence, bestFuzzy.token);
        }

        return defaultResult();
    }

    // ================================================================
    // Самообучение
    // ================================================================

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
            LearnedKeyword lk = new LearnedKeyword();
            lk.setKeyword(kw);
            lk.setCategory(category);
            lk.setSubcategory(subcategory);
            lk.setUserId(userId);
            lk.setUseCount(1);
            lk.setCreatedAt(LocalDateTime.now());
            learnedKeywordRepository.save(lk);
        }

        log.info("Learned: '{}' → {}/{} for user {}",
                kw, category.getName(),
                subcategory != null ? subcategory.getName() : "—", userId);
    }

    // ================================================================
    // Helpers
    // ================================================================

    public String normalize(String text) {
        return text.toLowerCase()
                .replaceAll("[0-9]+[.,]?[0-9]*", "")
                .replaceAll("[^а-яёa-z\\s-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    private DetectionResult defaultResult() {
        return new DetectionResult(defaultCategory, null, 0.0, null);
    }

    private BestMatch findBestFuzzyMatch(String[] tokens) {
        BestMatch best = null;
        for (String token : tokens) {
            if (token.length() < 3) continue;
            for (Map.Entry<String, Subcategory> entry : keywordIndex.entrySet()) {
                String kw = entry.getKey();
                if (Math.abs(token.length() - kw.length()) > 2) continue;
                int dist = levenshtein(token, kw);
                if (dist <= 2) {
                    double conf = 1.0 - (dist / (double) Math.max(token.length(), kw.length()));
                    if (best == null || conf > best.confidence)
                        best = new BestMatch(token, entry.getValue(), conf);
                }
            }
        }
        return (best != null && best.confidence >= 0.65) ? best : null;
    }

    private int levenshtein(String a, String b) {
        int la = a.length(), lb = b.length();
        int[][] dp = new int[la + 1][lb + 1];
        for (int i = 0; i <= la; i++) dp[i][0] = i;
        for (int j = 0; j <= lb; j++) dp[0][j] = j;
        for (int i = 1; i <= la; i++)
            for (int j = 1; j <= lb; j++)
                dp[i][j] = a.charAt(i-1) == b.charAt(j-1)
                        ? dp[i-1][j-1]
                        : 1 + Math.min(dp[i-1][j-1], Math.min(dp[i-1][j], dp[i][j-1]));
        return dp[la][lb];
    }

    // ================================================================
    // Result classes
    // ================================================================

    public static class DetectionResult {
        private final Category    category;
        private final Subcategory subcategory;
        private final double      confidence;
        private final String      matchedKeyword;

        public DetectionResult(Category cat, Subcategory sub, double confidence, String matched) {
            this.category       = cat;
            this.subcategory    = sub;
            this.confidence     = confidence;
            this.matchedKeyword = matched;
        }

        public Category    getCategory()       { return category; }
        public Subcategory getSubcategory()    { return subcategory; }
        public double      getConfidence()     { return confidence; }
        public String      getMatchedKeyword() { return matchedKeyword; }

        public boolean isConfident() { return confidence >= 0.65; }

        public String display() {
            if (category == null) return "💰 Прочее";
            if (subcategory != null)
                return category.getDisplayName() + " → " + subcategory.getDisplayName();
            return category.getDisplayName();
        }
    }

    private static class BestMatch {
        final String      token;
        final Subcategory subcategory;
        final double      confidence;
        BestMatch(String t, Subcategory s, double c) { token=t; subcategory=s; confidence=c; }
    }
}