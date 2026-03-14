package com.monetka.service;

import com.monetka.model.Category;
import com.monetka.model.LearnedKeyword;
import com.monetka.model.Subcategory;
import com.monetka.repository.CategoryRepository;
import com.monetka.repository.LearnedKeywordRepository;
import com.monetka.repository.SubcategoryRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class CategoryDetectionService {

    private static final Logger log = LoggerFactory.getLogger(CategoryDetectionService.class);

    private final CategoryRepository       categoryRepository;
    private final SubcategoryRepository    subcategoryRepository;
    private final LearnedKeywordRepository learnedKeywordRepository;

    private final Map<String, Subcategory> keywordIndex = new HashMap<>();
    private Category defaultCategory;

    /**
     * Слова которые НИКОГДА не учатся как ключевые — слишком общие.
     * Если пользователь пишет "оплата за свет" и выбирает Коммуналку,
     * мы учим "свет", а не "оплата", "за" и т.д.
     */
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "оплата","оплатил","оплатила","оплачено","оплатить","заплатил","заплатила",
            "купил","купила","купили","покупка","потратил","потратила","трата",
            "перевел","перевела","перевод","отправил","кинул","скинул",
            "за","на","в","из","от","до","к","по","при","без","под","над","про","для",
            "со","об","ради","через","между","среди","вокруг",
            "сегодня","вчера","завтра","утром","вечером","ночью","днём","день",
            "раз","немного","много","мало","очень","ещё","уже","тоже","также",
            "новый","новая","новое","старый","старая","другой","другая",
            "мой","моя","моё","мои","наш","свой","своя",
            "это","этот","эта","тот","та",
            "деньги","сумма","итого","всего","нужно","надо","хочу",
            "стоит","стоимость","цена","счёт","счет","платёж","платеж",
            "обычно","просто","только","вообще","нет","да","ок","окей",
            "один","одна","два","две","три","четыре","пять","шесть","семь","восемь","девять","десять"
    ));

    // AiInsightService инжектируется через setter чтобы избежать circular dependency
    // CategoryDetectionService ← MessageHandler ← AiInsightService (если через конструктор)
    private AiInsightService aiInsightService;

    public void setAiInsightService(AiInsightService ai) { this.aiInsightService = ai; }

    public CategoryDetectionService(CategoryRepository categoryRepository,
                                    SubcategoryRepository subcategoryRepository,
                                    LearnedKeywordRepository learnedKeywordRepository) {
        this.categoryRepository       = categoryRepository;
        this.subcategoryRepository    = subcategoryRepository;
        this.learnedKeywordRepository = learnedKeywordRepository;
    }

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
        log.info("Category index built: {} keywords across {} categories", keywordIndex.size(), all.size());
    }

    public void reload() { buildIndex(); }

    @Transactional(readOnly = true)
    public DetectionResult detectCategory(String text, Long userId) {
        if (text == null || text.isBlank()) return defaultResult();
        String normalized = normalize(text);
        if (normalized.isBlank()) return defaultResult();
        String[] tokens = normalized.split("\\s+");

        // 1. Personal learned keywords — ALWAYS use, even if mapped to "Прочее"
        for (String token : tokens) {
            List<LearnedKeyword> learned = learnedKeywordRepository.findByKeywordAndUser(token, userId);
            if (!learned.isEmpty()) {
                LearnedKeyword lk = learned.get(0);
                // Read isDefault while session is open
                boolean isDefault = lk.getCategory() != null && lk.getCategory().isDefault();
                return new DetectionResult(lk.getCategory(), lk.getSubcategory(), 1.0, token, true, isDefault);
            }
        }

        // 2. Exact keyword match
        for (String token : tokens) {
            Subcategory sub = keywordIndex.get(token);
            if (sub != null) {
                boolean isDefault = sub.getCategory().isDefault();
                return new DetectionResult(sub.getCategory(), sub, 1.0, token, false, isDefault);
            }
        }

        // 3. Multi-word phrase
        for (int len = tokens.length; len >= 2; len--) {
            for (int i = 0; i <= tokens.length - len; i++) {
                String phrase = String.join(" ", Arrays.copyOfRange(tokens, i, i + len));
                Subcategory sub = keywordIndex.get(phrase);
                if (sub != null) {
                    boolean isDefault = sub.getCategory().isDefault();
                    return new DetectionResult(sub.getCategory(), sub, 1.0, phrase, false, isDefault);
                }
            }
        }

        // 4. Substring match
        for (String token : tokens) {
            for (Map.Entry<String, Subcategory> entry : keywordIndex.entrySet()) {
                String kw = entry.getKey();
                if (token.contains(kw) || kw.contains(token)) {
                    double conf = (double) Math.min(token.length(), kw.length()) / Math.max(token.length(), kw.length());
                    if (conf >= 0.75) {
                        Subcategory sub = entry.getValue();
                        boolean isDefault = sub.getCategory().isDefault();
                        return new DetectionResult(sub.getCategory(), sub, conf, token, false, isDefault);
                    }
                }
            }
        }

        // 5. Fuzzy Levenshtein
        BestMatch best = findBestFuzzyMatch(tokens);
        if (best != null) {
            boolean isDefault = best.subcategory.getCategory().isDefault();
            return new DetectionResult(best.subcategory.getCategory(), best.subcategory,
                    best.confidence, best.token, false, isDefault);
        }

        // 6. AI категоризация — только если всё выше не сработало
        if (aiInsightService != null) {
            try {
                List<Category> allCats = categoryRepository.findAll();
                List<String> catNames = allCats.stream()
                        .map(Category::getName).collect(java.util.stream.Collectors.toList());

                // Строим карту подкатегорий для AI
                java.util.Map<String, List<String>> subcatMap = new java.util.LinkedHashMap<>();
                for (Category cat : allCats) {
                    List<String> subNames = cat.getSubcategories().stream()
                            .map(Subcategory::getName).collect(java.util.stream.Collectors.toList());
                    if (!subNames.isEmpty()) subcatMap.put(cat.getName(), subNames);
                }

                AiInsightService.AiCategory aiResult = aiInsightService.detectCategory(text, catNames, subcatMap);
                if (aiResult != null && aiResult.confidence >= 0.60) {
                    for (Category cat : allCats) {
                        if (cat.getName().equalsIgnoreCase(aiResult.category)) {
                            boolean isDefault = cat.isDefault();
                            // Ищем подкатегорию если AI её вернул
                            Subcategory matchedSub = null;
                            if (aiResult.subcategory != null && !aiResult.subcategory.isBlank()) {
                                matchedSub = cat.getSubcategories().stream()
                                        .filter(s -> s.getName().equalsIgnoreCase(aiResult.subcategory))
                                        .findFirst().orElse(null);
                            }
                            return new DetectionResult(cat, matchedSub, aiResult.confidence,
                                    normalized, false, isDefault);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("AI category step failed: {}", e.getMessage());
            }
        }

        return defaultResult();
    }

    @Transactional
    public void learnKeyword(String word, Category category, Subcategory subcategory, Long userId) {
        String kw = word.toLowerCase().trim();

        // Не учим стоп-слова — они слишком общие
        if (STOP_WORDS.contains(kw)) {
            log.debug("Skipping stop-word: '{}'", kw);
            return;
        }
        // Не учим слова короче 3 символов
        if (kw.length() < 3) {
            log.debug("Skipping too-short word: '{}'", kw);
            return;
        }

        Optional<LearnedKeyword> existing = learnedKeywordRepository.findByKeywordAndUserId(kw, userId);
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
            lk.setCreatedAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Bishkek")));
            learnedKeywordRepository.save(lk);
        }
        log.info("Learned: '{}' → {}/{} for user {}", kw, category.getName(),
                subcategory != null ? subcategory.getName() : "—", userId);
    }

    public String normalize(String text) {
        return text.toLowerCase()
                .replaceAll("[0-9]+[.,]?[0-9]*", "")
                .replaceAll("[^а-яёa-z\\s-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public List<Category> getAllCategories() { return categoryRepository.findAll(); }

    // ================================================================

    private DetectionResult defaultResult() {
        return new DetectionResult(defaultCategory, null, 0.0, null, false, true);
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
    // DetectionResult — immutable, all fields read while session is open
    // ================================================================

    public static class DetectionResult {
        private final Category    category;
        private final Subcategory subcategory;
        private final double      confidence;
        private final String      matchedKeyword;
        private final boolean     fromLearned;    // came from user's personal learned keywords
        private final boolean     defaultCategory; // is_default=true (read inside transaction)

        public DetectionResult(Category cat, Subcategory sub, double confidence,
                               String matched, boolean fromLearned, boolean defaultCategory) {
            this.category        = cat;
            this.subcategory     = sub;
            this.confidence      = confidence;
            this.matchedKeyword  = matched;
            this.fromLearned     = fromLearned;
            this.defaultCategory = defaultCategory;
        }

        public Category    getCategory()        { return category; }
        public Subcategory getSubcategory()     { return subcategory; }
        public double      getConfidence()      { return confidence; }
        public String      getMatchedKeyword()  { return matchedKeyword; }
        public boolean     isFromLearned()      { return fromLearned; }
        public boolean     isDefaultCategory()  { return defaultCategory; }

        /** Confident enough to auto-categorize without asking the user */
        public boolean isConfident() { return confidence >= 0.65; }

        /** Medium confidence — show "Возможно ты имел в виду?" suggestion */
        public boolean hasSuggestion() { return confidence >= 0.38 && confidence < 0.65 && subcategory != null; }

        public String suggestionLabel() {
            if (subcategory == null) return "";
            return (category != null && category.getEmoji() != null ? category.getEmoji() + " " : "")
                    + (subcategory.getEmoji() != null ? subcategory.getEmoji() + " " : "")
                    + subcategory.getName();
        }

        /**
         * Should we skip the "choose category" prompt?
         * Yes if: confident match AND (not default category OR came from learned keywords)
         * Learned keywords always auto-categorize, even if mapped to "Прочее"
         */
        public boolean shouldAutoSave() {
            return isConfident() && category != null && (fromLearned || !defaultCategory);
        }

        public String display() {
            if (category == null) return "💰 Прочее";
            if (subcategory != null) return category.getDisplayName() + " → " + subcategory.getDisplayName();
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