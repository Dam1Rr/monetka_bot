package com.monetka.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "emoji")
    private String emoji;

    @Column(name = "is_default")
    private boolean isDefault = false;

    @OneToMany(mappedBy = "category", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    private List<Subcategory> subcategories = new ArrayList<>();

    public Category() {}

    public Long getId()                           { return id; }
    public String getName()                       { return name; }
    public String getEmoji()                      { return emoji; }
    public boolean isDefault()                    { return isDefault; }
    public List<Subcategory> getSubcategories()   { return subcategories; }

    public void setId(Long id)                              { this.id = id; }
    public void setName(String name)                        { this.name = name; }
    public void setEmoji(String emoji)                      { this.emoji = emoji; }
    public void setDefault(boolean isDefault)               { this.isDefault = isDefault; }
    public void setSubcategories(List<Subcategory> s)       { this.subcategories = s; }

    public String getDisplayName() {
        return emoji != null ? emoji + " " + name : name;
    }
}