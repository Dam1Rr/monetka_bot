package com.monetka.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "categories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "emoji")
    private String emoji;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "category_keywords",
        joinColumns = @JoinColumn(name = "category_id")
    )
    @Column(name = "keyword")
    @Builder.Default
    private List<String> keywords = new ArrayList<>();

    @Column(name = "is_default")
    @Builder.Default
    private boolean isDefault = false;

    public String getDisplayName() {
        return emoji != null ? emoji + " " + name : name;
    }
}
