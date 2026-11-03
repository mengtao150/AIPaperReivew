package com.reviewer.scout.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "expert", uniqueConstraints = @UniqueConstraint(columnNames = {"scan_run_id", "name"}))
public class Expert {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "scan_run_id", nullable = false)
    private Long scanRunId;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(name = "name_en", columnDefinition = "VARCHAR(300)")
    private String nameEn;
    @Column(columnDefinition = "VARCHAR(500)")
    private String institution;
    @Column(columnDefinition = "VARCHAR(300)")
    private String department;
    @Column(name = "country_region", columnDefinition = "VARCHAR(100)")
    private String countryRegion;
    @Column(length = 50)
    private String orcid;
    @Column(name = "research_focus", columnDefinition = "TEXT")
    private String researchFocus;
    @Column(name = "total_score", columnDefinition = "DECIMAL(4,1)")
    private Double totalScore;
    @Column(name = "topic_outputs_36m")
    private Integer topicOutputs36m;
    @Column(columnDefinition = "TEXT")
    private String roles;
    @Column(name = "rising_star")
    private Boolean risingStar;
    @Column(length = 300)
    private String email;
    @Column(name = "email_type", length = 5)
    private String emailType;
    @Column(name = "email_source_url", columnDefinition = "VARCHAR(500)")
    private String emailSourceUrl;
    @Column(name = "email_snippet", columnDefinition = "TEXT")
    private String emailSnippet;
    @Column(name = "email_confidence", length = 20)
    private String emailConfidence;
    @Column(name = "coi_note", columnDefinition = "TEXT")
    private String coiNote;
    @Column(length = 30)
    private String status;
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
