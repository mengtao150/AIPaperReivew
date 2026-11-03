package com.reviewer.scout.repository;

import com.reviewer.scout.entity.Expert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExpertRepository extends JpaRepository<Expert, Long> {
    Page<Expert> findByScanRunId(Long scanRunId, Pageable pageable);
    List<Expert> findByScanRunIdOrderByTotalScoreDesc(Long scanRunId);
    long countByScanRunId(Long scanRunId);
}
