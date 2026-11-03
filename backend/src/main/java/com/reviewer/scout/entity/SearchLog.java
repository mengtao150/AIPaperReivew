package com.reviewer.scout.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "search_log")
public class SearchLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "scan_run_id", nullable = false)
    private Long scanRunId;
    @Column(nullable = false, length = 50)
    private String source;
    @Column(columnDefinition = "TEXT")
    private String query;
    @Column(columnDefinition = "VARCHAR(500)")
    private String filters;
    private Integer hits;
    private Integer retrieved;
    @Column(columnDefinition = "VARCHAR(500)")
    private String note;
    @Column(name = "executed_at")
    private LocalDateTime executedAt;
}
