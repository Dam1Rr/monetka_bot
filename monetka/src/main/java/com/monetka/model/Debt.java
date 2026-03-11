package com.monetka.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "debts")
public class Debt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String name;

    /** Слово-триггер — юзер пишет «зарплатный 13000», бот находит этот долг */
    @Column(name = "trigger_word", nullable = false)
    private String triggerWord;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private BigDecimal remaining;

    @Column(name = "monthly_payment", nullable = false)
    private BigDecimal monthlyPayment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    // ── getters / setters ──

    public Long         getId()              { return id; }
    public User         getUser()            { return user; }
    public void         setUser(User u)      { this.user = u; }
    public String       getName()            { return name; }
    public void         setName(String n)    { this.name = n; }
    public String       getTriggerWord()     { return triggerWord; }
    public void         setTriggerWord(String t) { this.triggerWord = t.toLowerCase().trim(); }
    public BigDecimal   getTotalAmount()     { return totalAmount; }
    public void         setTotalAmount(BigDecimal a) { this.totalAmount = a; }
    public BigDecimal   getRemaining()       { return remaining; }
    public void         setRemaining(BigDecimal r)   { this.remaining = r; }
    public BigDecimal   getMonthlyPayment()  { return monthlyPayment; }
    public void         setMonthlyPayment(BigDecimal m) { this.monthlyPayment = m; }
    public LocalDateTime getCreatedAt()      { return createdAt; }
    public void          setCreatedAt(LocalDateTime t) { this.createdAt = t; }
    public LocalDateTime getClosedAt()       { return closedAt; }
    public void          setClosedAt(LocalDateTime t)  { this.closedAt = t; }

    public boolean isClosed() { return closedAt != null; }

    /** Сколько месяцев осталось (округление вверх) */
    public int monthsLeft() {
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) return 0;
        if (monthlyPayment.compareTo(BigDecimal.ZERO) <= 0) return 0;
        return (int) Math.ceil(remaining.doubleValue() / monthlyPayment.doubleValue());
    }

    /** Процент выплаченного */
    public int paidPercent() {
        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) return 0;
        BigDecimal paid = totalAmount.subtract(remaining);
        return (int) Math.round(paid.doubleValue() / totalAmount.doubleValue() * 100);
    }

    /** Текстовый прогресс-бар 10 символов */
    public String progressBar() {
        int pct  = paidPercent();
        int fill = pct / 10;
        return "\u2588".repeat(fill) + "\u2591".repeat(10 - fill) + " " + pct + "%";
    }
}