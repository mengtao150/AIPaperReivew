package com.reviewer.scout.repository;
import com.reviewer.scout.entity.EvidenceDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface EvidenceDocumentRepository extends JpaRepository<EvidenceDocument,Long> {
    Optional<EvidenceDocument> findByWorkspaceAndScanRunId(String workspace,Long scanRunId);
}
