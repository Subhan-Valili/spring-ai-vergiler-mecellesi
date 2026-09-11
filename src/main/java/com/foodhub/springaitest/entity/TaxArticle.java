package com.foodhub.springaitest.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;


@Entity
@Table(name = "tax_articles")
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Getter
@Setter
@ToString(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TaxArticle extends BaseEntity{

    @Column(name = "article_number", nullable = false)
    String articleNumber;

    @Column(name = "title")
    String title;

    @Column(name = "chapter")
    String chapter;

    @Builder.Default
    @Column(name = "active")
    boolean active = true;

    @Builder.Default
    @ToString.Exclude
    @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, orphanRemoval = true)
    List<TaxSection> sections = new ArrayList<>();
}