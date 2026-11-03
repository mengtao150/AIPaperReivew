package com.reviewer.scout.repository;

import com.reviewer.scout.entity.SearchLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SearchLogRepository extends JpaRepository<SearchLog, Long> {
    List<SearchLog> findByScanRunIdOrderByExecutedAtAsc(Long scanRunId);
}
