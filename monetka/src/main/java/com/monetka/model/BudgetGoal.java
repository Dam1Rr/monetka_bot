package com.monetka.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "budget_goals",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "category_id"}))
public class BudgetGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public BudgetGoal() {}

    public BudgetGoal(User user, Category category, BigDecimal amount) {
        this.user      = user;
        this.category  = category;
        this.amount    = amount;
        this.createdAt = LocalDateTime.now(java.time.ZoneId.of("Asia/Bishkek"));
        this.updatedAt = LocalDateTime.now(java.time.ZoneId.of("Asia/Bishkek"));
    }

    public Long getId()              { return id; }
    public User getUser()            { return user; }
    public Category getCategory()    { return category; }
    public BigDecimal getAmount()    { return amount; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setAmount(BigDecimal amount) {
        this.amount    = amount;
        this.updatedAt = LocalDateTime.now(java.time.ZoneId.of("Asia/Bishkek"));
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now(java.time.ZoneId.of("Asia/Bishkek"));
        if (updatedAt == null) updatedAt = LocalDateTime.now(java.time.ZoneId.of("Asia/Bishkek"));
    }

    @PreUpdate
    void preUpdate() { updatedAt = LocalDateTime.now(java.time.ZoneId.of("Asia/Bishkek")); }
}