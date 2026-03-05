package com.monetka.model;

import com.monetka.model.enums.TransactionType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "description")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subcategory_id")
    private Subcategory subcategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TransactionType type;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Transaction() {}

    public Long getId()                  { return id; }
    public User getUser()                { return user; }
    public BigDecimal getAmount()        { return amount; }
    public String getDescription()       { return description; }
    public Category getCategory()        { return category; }
    public Subcategory getSubcategory()  { return subcategory; }
    public TransactionType getType()     { return type; }
    public LocalDateTime getCreatedAt()  { return createdAt; }

    public void setId(Long id)                      { this.id = id; }
    public void setUser(User user)                  { this.user = user; }
    public void setAmount(BigDecimal amount)        { this.amount = amount; }
    public void setDescription(String description)  { this.description = description; }
    public void setCategory(Category category)      { this.category = category; }
    public void setSubcategory(Subcategory sub)     { this.subcategory = sub; }
    public void setType(TransactionType type)       { this.type = type; }

    @PrePersist
    void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}