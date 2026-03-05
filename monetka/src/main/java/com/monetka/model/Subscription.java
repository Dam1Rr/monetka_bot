package com.monetka.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Subscription() {}

    public Long getId()              { return id; }
    public User getUser()            { return user; }
    public String getName()          { return name; }
    public BigDecimal getAmount()    { return amount; }
    public Category getCategory()    { return category; }
    public LocalDate getStartDate()  { return startDate; }
    public LocalDate getEndDate()    { return endDate; }
    public boolean isActive()        { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(Long id)              { this.id = id; }
    public void setUser(User user)          { this.user = user; }
    public void setName(String name)        { this.name = name; }
    public void setAmount(BigDecimal a)     { this.amount = a; }
    public void setCategory(Category c)     { this.category = c; }
    public void setStartDate(LocalDate d)   { this.startDate = d; }
    public void setEndDate(LocalDate d)     { this.endDate = d; }
    public void setActive(boolean active)   { this.active = active; }

    public Long daysUntilExpiry() {
        if (endDate == null) return null;
        long days = ChronoUnit.DAYS.between(LocalDate.now(), endDate);
        return days >= 0 ? days : null;
    }

    public boolean isExpired() {
        return endDate != null && LocalDate.now().isAfter(endDate);
    }

    @PrePersist
    void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}