package com.reviewer.scout.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "article_category", uniqueConstraints = @UniqueConstraint(columnNames = {"article_id", "category_id"}))
public class ArticleCategory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "article_id", nullable = false)
    private Long articleId;
    @Column(name = "category_id", nullable = false)
    private Long categoryId;
    @Column(name = "is_primary")
    private Boolean isPrimary;
    @Column(columnDefinition = "VARCHAR(300)")
    private String basis;
    @Column(name = "subcategory", columnDefinition = "VARCHAR(100)")
    private String subcategory;
}
