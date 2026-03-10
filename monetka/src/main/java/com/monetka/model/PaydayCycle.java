package com.monetka.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents one pay cycle for a user.
 * A new cycle starts every time user records salary/income.
 * Only one cycle can be active at a time per user.
 */
@Entity
@Table(name = "payday_cycles")
public class PaydayCycle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "total_income", nullable = false)
    private BigDecimal totalIncome;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public PaydayCycle() {}

    public PaydayCycle(User user, BigDecimal totalIncome) {
        this.user        = user;
        this.startDate   = LocalDate.now(java.time.ZoneId.of("Asia/Bishkek"));
        this.totalIncome = totalIncome;
        this.active      = true;
        this.createdAt   = LocalDateTime.now(java.time.ZoneId.of("Asia/Bishkek"));
        this.updatedAt   = LocalDateTime.now(java.time.ZoneId.of("Asia/Bishkek"));
    }

    public Long getId()              { return id; }
    public User getUser()            { return user; }
    public LocalDate getStartDate()  { return startDate; }
    public BigDecimal getTotalIncome() { return totalIncome; }
    public boolean isActive()        { return active; }

    public void setActive(boolean active)            { this.active = active; this.updatedAt = LocalDateTime.now(java.time.ZoneId.of("Asia/Bishkek")); }
    public void setTotalIncome(BigDecimal amount)    { this.totalIncome = amount; this.updatedAt = LocalDateTime.now(java.time.ZoneId.of("Asia/Bishkek")); }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now(java.time.ZoneId.of("Asia/Bishkek"));
        if (updatedAt == null) updatedAt = LocalDateTime.now(java.time.ZoneId.of("Asia/Bishkek"));
    }

    @PreUpdate
    void preUpdate() { updatedAt = LocalDateTime.now(java.time.ZoneId.of("Asia/Bishkek")); }
}