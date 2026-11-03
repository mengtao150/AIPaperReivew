package com.reviewer.scout.repository;

import com.reviewer.scout.entity.TopicLead;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TopicLeadRepository extends JpaRepository<TopicLead, Long> {
    List<TopicLead> findByScanRunIdOrderByCreatedAtDesc(Long scanRunId);
}
