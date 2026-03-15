package com.monetka.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_reminders")
public class UserReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = false;

    @Column(name = "hour_morning", nullable = false)
    private int hourMorning = 13;

    @Column(name = "hour_evening", nullable = false)
    private int hourEvening = 21;

    @Column(name = "morning_enabled", nullable = false)
    private boolean morningEnabled = true;

    @Column(name = "evening_enabled", nullable = false)
    private boolean eveningEnabled = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public UserReminder() {}

    public UserReminder(User user) {
        this.user = user;
    }

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now(java.time.ZoneId.of("Asia/Bishkek"));
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now(java.time.ZoneId.of("Asia/Bishkek"));
    }

    // ── Getters / Setters ──
    public Long getId()                    { return id; }
    public User getUser()                  { return user; }
    public boolean isEnabled()             { return enabled; }
    public int getHourMorning()            { return hourMorning; }
    public int getHourEvening()            { return hourEvening; }
    public boolean isMorningEnabled()      { return morningEnabled; }
    public boolean isEveningEnabled()      { return eveningEnabled; }
    public LocalDateTime getCreatedAt()    { return createdAt; }
    public LocalDateTime getUpdatedAt()    { return updatedAt; }

    public void setUser(User user)                    { this.user = user; }
    public void setEnabled(boolean enabled)           { this.enabled = enabled; }
    public void setHourMorning(int h)                 { this.hourMorning = h; }
    public void setHourEvening(int h)                 { this.hourEvening = h; }
    public void setMorningEnabled(boolean b)          { this.morningEnabled = b; }
    public void setEveningEnabled(boolean b)          { this.eveningEnabled = b; }
}