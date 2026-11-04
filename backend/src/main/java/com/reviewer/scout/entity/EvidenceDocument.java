package com.reviewer.scout.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data @Entity
@Table(name="evidence_document", uniqueConstraints=@UniqueConstraint(columnNames={"workspace", "scan_run_id"}))
public class EvidenceDocument {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=64) private String workspace;
    @Column(name="scan_run_id",nullable=false) private Long scanRunId;
    @Column(nullable=false) private long revision;
    @Version private Long lockVersion;
    @Column(nullable=false,columnDefinition="LONGTEXT") private String document;
    private LocalDateTime updatedAt;
}
