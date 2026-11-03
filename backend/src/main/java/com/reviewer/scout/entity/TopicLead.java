package com.reviewer.scout.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "topic_lead")
public class TopicLead {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "scan_run_id", nullable = false)
    private Long scanRunId;
    @Column(nullable = false, length = 30)
    private String dimension;
    @Column(name = "title_zh", nullable = false, columnDefinition = "VARCHAR(300)")
    private String titleZh;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(name = "supporting_pmids", columnDefinition = "TEXT")
    private String supportingPmids;
    @Column(name = "article_count")
    private Integer articleCount;
    @Column(name = "novelty_status", length = 20)
    private String noveltyStatus;
    @Column(length = 20)
    private String confidence;
    @Column(columnDefinition = "TEXT")
    private String verdict;
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
