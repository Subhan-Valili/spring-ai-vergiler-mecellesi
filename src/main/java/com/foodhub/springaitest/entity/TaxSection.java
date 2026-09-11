package com.foodhub.springaitest.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "tax_sections")
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Getter
@Setter
@ToString(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TaxSection extends BaseEntity {

    @Column(name = "section_number", nullable = false)
    String sectionNumber;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    String content;

    @Column(name = "order_index", nullable = false)
    Integer orderIndex;

    @Column(name = "synced_to_vector_store")
    @Builder.Default
    boolean syncedToVectorStore = false;

    @Column(name = "vector_id")
    String vectorId;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    TaxArticle article;
}