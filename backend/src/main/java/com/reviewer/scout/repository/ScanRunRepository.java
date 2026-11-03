package com.reviewer.scout.repository;

import com.reviewer.scout.entity.ScanRun;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ScanRunRepository extends JpaRepository<ScanRun, Long> {
    Optional<ScanRun> findByRunId(String runId);
}
