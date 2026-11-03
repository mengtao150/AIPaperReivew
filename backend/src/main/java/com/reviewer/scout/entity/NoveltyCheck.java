package com.reviewer.scout.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "novelty_check")
public class NoveltyCheck {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "topic_lead_id", nullable = false)
    private Long topicLeadId;
    @Column(name = "layer_no", nullable = false)
    private Integer layerNo;
    @Column(name = "layer_name", nullable = false, length = 100)
    private String layerName;
    @Column(nullable = false, length = 30)
    private String status;
    @Column(columnDefinition = "TEXT")
    private String detail;
    @Column(name = "sources_queried", columnDefinition = "TEXT")
    private String sourcesQueried;
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
