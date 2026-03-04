package com.monetka.service;

import com.monetka.model.Category;
import com.monetka.repository.CategoryRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Detects category from free-text description using keyword matching.
 *
 * Algorithm:
 *  1. Normalize input (lowercase, trim)
 *  2. Check each word against keyword dictionary
 *  3. Return first match, or default "Прочее" category
 *
 * Scales well: to improve accuracy later, replace with ML model
 * or a more advanced tokenization approach.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryDetectionService {

    private final CategoryRepository categoryRepository;

    /** keyword (lowercase) → Category */
    private final Map<String, Category> keywordIndex = new HashMap<>();
    private Category defaultCategory;

    @PostConstruct
    void buildIndex() {
        List<Category> all = categoryRepository.findAll();
        for (Category category : all) {
            if (category.isDefault()) {
                defaultCategory = category;
            }
            for (String kw : category.getKeywords()) {
                keywordIndex.put(kw.toLowerCase().trim(), category);
            }
        }
        log.info("Category index built: {} keywords across {} categories",
            keywordIndex.size(), all.size());
    }

    /**
     * Detect category from description text.
     * @param description e.g. "шаурма 300" or "кофе латте"
     * @return matched Category or default
     */
    public Category detect(String description) {
        if (description == null || description.isBlank()) {
            return defaultCategory;
        }

        String[] tokens = description.toLowerCase().split("[\\s,;.!?]+");

        for (String token : tokens) {
            Category match = keywordIndex.get(token);
            if (match != null) {
                log.debug("Keyword '{}' → category '{}'", token, match.getName());
                return match;
            }
        }

        // Try substring matching for compound words
        String normalized = description.toLowerCase();
        for (Map.Entry<String, Category> entry : keywordIndex.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return defaultCategory;
    }

    /** Force reload of the index (e.g. after admin adds new keywords) */
    public void reload() {
        keywordIndex.clear();
        buildIndex();
    }
}
