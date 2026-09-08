package com.foodhub.springaitest.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "tax_articles")
@Data
public class TaxArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "article_number", nullable = false)
    private String articleNumber; // "Maddə 164"

    @Column(name = "chapter")
    private String chapter; // "VIII Fəsil. ƏDV"

    @Column(name = "source_title")
    private String sourceTitle; // hansı PDF-dən gəlib

    @Column(name = "order_index")
    private Integer orderIndex; // PDF-dəki sıra (manual review üçün faydalı)

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "active")
    private boolean active = true;

    @Column(name = "synced_to_vector_store")
    private boolean syncedToVectorStore = false;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}