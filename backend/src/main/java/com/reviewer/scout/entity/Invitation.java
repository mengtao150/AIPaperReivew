package com.reviewer.scout.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "invitation")
public class Invitation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "scan_run_id", nullable = false)
    private Long scanRunId;
    @Column(name = "expert_id", nullable = false)
    private Long expertId;
    @Column(name = "template_id")
    private Long templateId;
    @Column(columnDefinition = "VARCHAR(500)")
    private String subject;
    @Column(columnDefinition = "MEDIUMTEXT")
    private String body;
    @Column(length = 30)
    private String status;
    @Column(name = "message_id", columnDefinition = "VARCHAR(200)")
    private String messageId;
    @Column(name = "sent_at")
    private LocalDateTime sentAt;
    @Column(name = "replied_at")
    private LocalDateTime repliedAt;
    @Column(name = "reply_class", length = 30)
    private String replyClass;
    @Column(name = "reply_body", columnDefinition = "TEXT")
    private String replyBody;
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
