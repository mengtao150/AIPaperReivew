package com.reviewer.scout.repository;

import com.reviewer.scout.entity.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {
    List<Invitation> findByScanRunId(Long scanRunId);
    List<Invitation> findByScanRunIdAndStatus(Long scanRunId, String status);
    List<Invitation> findByExpertId(Long expertId);
}
