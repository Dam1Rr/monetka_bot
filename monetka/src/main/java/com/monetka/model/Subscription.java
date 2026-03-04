package com.monetka.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscriptions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = "user")
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

    /** Дата начала подписки */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Дата окончания подписки (null = бессрочная) */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    /** Дней до окончания. null если бессрочная или уже истекла */
    public Long daysUntilExpiry() {
        if (endDate == null) return null;
        long days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), endDate);
        return days >= 0 ? days : null;
    }

    /** Истекла ли подписка */
    public boolean isExpired() {
        return endDate != null && LocalDate.now().isAfter(endDate);
    }
}