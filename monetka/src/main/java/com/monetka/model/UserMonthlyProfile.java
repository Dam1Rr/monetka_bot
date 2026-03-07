package com.monetka.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_monthly_profiles")
public class UserMonthlyProfile {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false) private Integer month;
    @Column(nullable = false) private Integer year;

    @Column(name = "personality_type", length = 50)
    private String personalityType;

    @Column(name = "peak_day",  length = 20) private String peakDay;
    @Column(name = "peak_hour")              private Integer peakHour;
    @Column(name = "top_category",length=100)private String topCategory;
    @Column(name = "savings_pct")            private Integer savingsPct    = 0;
    @Column(name = "night_pct")              private Integer nightPct      = 0;
    @Column(name = "impulse_score")          private Integer impulseScore  = 0;
    @Column(name = "discipline_score")       private Integer disciplineScore = 0;
    @Column(name = "created_at")             private LocalDateTime createdAt = LocalDateTime.now();

    public UserMonthlyProfile() {}

    // Getters & setters
    public Long getId()                  { return id; }
    public User getUser()                { return user; }
    public void setUser(User u)          { this.user = u; }
    public Integer getMonth()            { return month; }
    public void setMonth(Integer m)      { this.month = m; }
    public Integer getYear()             { return year; }
    public void setYear(Integer y)       { this.year = y; }
    public String getPersonalityType()               { return personalityType; }
    public void setPersonalityType(String t)         { this.personalityType = t; }
    public String getPeakDay()                       { return peakDay; }
    public void setPeakDay(String d)                 { this.peakDay = d; }
    public Integer getPeakHour()                     { return peakHour; }
    public void setPeakHour(Integer h)               { this.peakHour = h; }
    public String getTopCategory()                   { return topCategory; }
    public void setTopCategory(String c)             { this.topCategory = c; }
    public Integer getSavingsPct()                   { return savingsPct; }
    public void setSavingsPct(Integer p)             { this.savingsPct = p; }
    public Integer getNightPct()                     { return nightPct; }
    public void setNightPct(Integer p)               { this.nightPct = p; }
    public Integer getImpulseScore()                 { return impulseScore; }
    public void setImpulseScore(Integer s)           { this.impulseScore = s; }
    public Integer getDisciplineScore()              { return disciplineScore; }
    public void setDisciplineScore(Integer s)        { this.disciplineScore = s; }
}