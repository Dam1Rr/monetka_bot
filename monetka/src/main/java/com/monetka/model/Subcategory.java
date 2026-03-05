package com.monetka.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "subcategories")
public class Subcategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "emoji")
    private String emoji;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "subcategory_keywords", joinColumns = @JoinColumn(name = "subcategory_id"))
    @Column(name = "keyword")
    private List<String> keywords = new ArrayList<>();

    public Subcategory() {}

    public Long getId()              { return id; }
    public String getName()          { return name; }
    public String getEmoji()         { return emoji; }
    public Category getCategory()    { return category; }
    public List<String> getKeywords(){ return keywords; }

    public void setId(Long id)              { this.id = id; }
    public void setName(String name)        { this.name = name; }
    public void setEmoji(String emoji)      { this.emoji = emoji; }
    public void setCategory(Category c)     { this.category = c; }
    public void setKeywords(List<String> k) { this.keywords = k; }

    public String getDisplayName() {
        return emoji != null ? emoji + " " + name : name;
    }
}