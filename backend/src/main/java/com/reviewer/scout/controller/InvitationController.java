package com.reviewer.scout.controller;

import com.reviewer.scout.entity.Expert;
import com.reviewer.scout.entity.Invitation;
import com.reviewer.scout.repository.ExpertRepository;
import com.reviewer.scout.repository.InvitationRepository;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/invitations")
public class InvitationController {

    private final InvitationRepository invitationRepo;
    private final ExpertRepository expertRepo;

    public InvitationController(InvitationRepository invitationRepo, ExpertRepository expertRepo) {
        this.invitationRepo = invitationRepo;
        this.expertRepo = expertRepo;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody InvitationRequest req) {
        Invitation inv = new Invitation();
        inv.setScanRunId(req.getScanRunId());
        inv.setExpertId(req.getExpertId());
        inv.setSubject(req.getSubject());
        inv.setBody(req.getBody());
        inv.setStatus("DRAFT");
        inv.setCreatedAt(LocalDateTime.now());
        return ResponseEntity.ok(invitationRepo.save(inv));
    }

    @GetMapping("/run/{scanRunId}")
    public ResponseEntity<List<Invitation>> list(@PathVariable Long scanRunId) {
        return ResponseEntity.ok(invitationRepo.findByScanRunId(scanRunId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody InvitationRequest req) {
        return invitationRepo.findById(id).map(inv -> {
            if (req.getSubject() != null) inv.setSubject(req.getSubject());
            if (req.getBody() != null) inv.setBody(req.getBody());
            if (req.getStatus() != null) inv.setStatus(req.getStatus());
            return ResponseEntity.ok(invitationRepo.save(inv));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/send")
    public ResponseEntity<?> send(@PathVariable Long id) {
        return invitationRepo.findById(id).map(inv -> {
            if (!"DRAFT".equals(inv.getStatus()) && !"APPROVED".equals(inv.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid status: " + inv.getStatus()));
            }
            // Simulate send (real implementation would use JavaMailSender)
            inv.setStatus("SENT");
            inv.setSentAt(LocalDateTime.now());
            inv.setMessageId("<rs-" + System.currentTimeMillis() + "@reviewer-scout.local>");
            invitationRepo.save(inv);
            expertRepo.findById(inv.getExpertId()).ifPresent(e -> {
                e.setStatus("SENT");
                expertRepo.save(e);
            });
            return ResponseEntity.ok(inv);
        }).orElse(ResponseEntity.notFound().build());
    }

    @Data
    public static class InvitationRequest {
        private Long scanRunId;
        private Long expertId;
        private Long templateId;
        private String subject;
        private String body;
        private String status;
    }
}
