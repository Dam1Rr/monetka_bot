package com.monetka.model;

import com.monetka.model.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"transactions", "subscriptions"})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_id", unique = true, nullable = false)
    private Long telegramId;

    @Column(name = "username")
    private String username;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status = UserStatus.PENDING;

    @Column(name = "balance", nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Конструктор для регистрации */
    public static User create(Long telegramId, String username,
                              String firstName, String lastName) {
        User u = new User();
        u.telegramId = telegramId;
        u.username   = username;
        u.firstName  = firstName;
        u.lastName   = lastName;
        u.status     = UserStatus.PENDING;
        u.balance    = BigDecimal.ZERO;
        return u;
    }

    public String getDisplayName() {
        if (firstName != null && !firstName.isBlank()) return firstName;
        if (username  != null && !username.isBlank())  return "@" + username;
        return "User#" + telegramId;
    }

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}