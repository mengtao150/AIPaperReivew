package com.reviewer.scout.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "category")
public class Category {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "scan_run_id", nullable = false)
    private Long scanRunId;
    @Column(name = "cat_id", nullable = false, length = 10)
    private String catId;
    @Column(name = "name_zh", nullable = false, length = 100)
    private String nameZh;
    @Column(name = "article_count")
    private Integer articleCount;
    @Column(name = "rct_share", columnDefinition = "DECIMAL(5,4)")
    private Double rctShare;
    @Column(name = "theme_zh", columnDefinition = "TEXT")
    private String themeZh;
    @Column(name = "sort_order")
    private Integer sortOrder;
}
