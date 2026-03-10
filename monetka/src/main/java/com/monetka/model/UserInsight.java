package com.monetka.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_insights")
public class UserInsight {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "trigger_key", nullable = false, length = 100)
    private String triggerKey;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "month", nullable = false)
    private Integer month;

    @Column(name = "year", nullable = false)
    private Integer year;

    public UserInsight() {}

    public UserInsight(User user, String triggerKey, int month, int year) {
        this.user       = user;
        this.triggerKey = triggerKey;
        this.sentAt     = LocalDateTime.now(java.time.ZoneId.of("Asia/Bishkek"));
        this.month      = month;
        this.year       = year;
    }

    public Long getId()              { return id; }
    public User getUser()            { return user; }
    public String getTriggerKey()    { return triggerKey; }
    public LocalDateTime getSentAt() { return sentAt; }
    public Integer getMonth()        { return month; }
    public Integer getYear()         { return year; }
}