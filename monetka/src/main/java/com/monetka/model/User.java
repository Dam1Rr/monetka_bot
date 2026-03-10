package com.monetka.model;

import com.monetka.model.enums.UserStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
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

    @Column(name = "blocked_bot", nullable = false)
    private boolean blockedBot = false;

    @Column(name = "blocked_at")
    private LocalDateTime blockedAt;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    public User() {}

    public static User create(Long telegramId, String username, String firstName, String lastName) {
        User u = new User();
        u.telegramId = telegramId;
        u.username   = username;
        u.firstName  = firstName;
        u.lastName   = lastName;
        u.status     = UserStatus.PENDING;
        u.balance    = BigDecimal.ZERO;
        return u;
    }

    public Long getId()                         { return id; }
    public Long getTelegramId()                 { return telegramId; }
    public String getUsername()                 { return username; }
    public String getFirstName()                { return firstName; }
    public String getLastName()                 { return lastName; }
    public UserStatus getStatus()               { return status; }
    public BigDecimal getBalance()              { return balance; }
    public LocalDateTime getCreatedAt()         { return createdAt; }
    public LocalDateTime getUpdatedAt()         { return updatedAt; }

    public void setId(Long id)                          { this.id = id; }
    public void setTelegramId(Long telegramId)          { this.telegramId = telegramId; }
    public void setUsername(String username)            { this.username = username; }
    public void setFirstName(String firstName)          { this.firstName = firstName; }
    public void setLastName(String lastName)            { this.lastName = lastName; }
    public void setStatus(UserStatus status)            { this.status = status; }
    public void setBalance(BigDecimal balance)          { this.balance = balance; }

    public boolean isBlockedBot()               { return blockedBot; }
    public LocalDateTime getBlockedAt()         { return blockedAt; }
    public LocalDateTime getLastSeenAt()        { return lastSeenAt; }

    public void setBlockedBot(boolean b)        { this.blockedBot = b; }
    public void setBlockedAt(LocalDateTime t)   { this.blockedAt = t; }
    public void setLastSeenAt(LocalDateTime t)  { this.lastSeenAt = t; }

    public String getDisplayName() {
        if (firstName != null && !firstName.isBlank()) return firstName;
        if (username  != null && !username.isBlank())  return "@" + username;
        return "User#" + telegramId;
    }

    @PrePersist
    void prePersist() { createdAt = LocalDateTime.now(); }

    @PreUpdate
    void preUpdate()  { updatedAt = LocalDateTime.now(); }
}