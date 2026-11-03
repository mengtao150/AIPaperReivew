package com.reviewer.scout.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "scan_run")
public class ScanRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "run_id", unique = true, nullable = false, length = 64)
    private String runId;
    @Column(nullable = false, columnDefinition = "VARCHAR(500)")
    private String topic;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String query;
    @Column(columnDefinition = "TEXT")
    private String sourceQuery;
    private String dateField;
    private String textAvailability;
    private String sortOrder;
    @Column(columnDefinition = "TEXT")
    private String queryTranslation;
    @Column(columnDefinition = "TEXT")
    private String searchWarnings;
    private java.time.LocalDate dateFrom;
    private java.time.LocalDate dateTo;
    @Column(columnDefinition = "VARCHAR(500)")
    private String articleTypes;
    @Column(length = 20)
    private String language;
    @Column(length = 20)
    private String status;
    private Integer totalHits;
    private Integer fetchedCount;
    private Integer classifiedCount;
    private Integer unclassifiedCount;
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
