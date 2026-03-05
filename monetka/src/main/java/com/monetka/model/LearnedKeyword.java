package com.monetka.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "learned_keywords",
        uniqueConstraints = @UniqueConstraint(columnNames = {"keyword", "user_id"}))
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

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "use_count")
    private int useCount = 1;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public LearnedKeyword() {}

    public Long getId()              { return id; }
    public String getKeyword()       { return keyword; }
    public Category getCategory()    { return category; }
    public Subcategory getSubcategory() { return subcategory; }
    public Long getUserId()          { return userId; }
    public int getUseCount()         { return useCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(Long id)                  { this.id = id; }
    public void setKeyword(String keyword)      { this.keyword = keyword; }
    public void setCategory(Category c)         { this.category = c; }
    public void setSubcategory(Subcategory s)   { this.subcategory = s; }
    public void setUserId(Long userId)          { this.userId = userId; }
    public void setUseCount(int useCount)       { this.useCount = useCount; }
    public void setCreatedAt(LocalDateTime dt)  { this.createdAt = dt; }

    @PrePersist
    void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}