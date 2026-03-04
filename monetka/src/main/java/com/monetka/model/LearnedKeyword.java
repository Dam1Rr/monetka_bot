package com.monetka.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Слова которые пользователь обучил бота вручную.
 * Хранятся в БД и имеют приоритет над словарём по умолчанию.
 */
@Entity
@Table(name = "learned_keywords",
        uniqueConstraints = @UniqueConstraint(columnNames = {"keyword", "user_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearnedKeyword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "keyword", nullable = false)
    private String keyword;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subcategory_id")
    private Subcategory subcategory;

    /** null = глобальное, не null = персональное для пользователя */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "use_count")
    @Builder.Default
    private int useCount = 1;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}