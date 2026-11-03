package com.reviewer.scout.repository;

import com.reviewer.scout.entity.NoveltyCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NoveltyCheckRepository extends JpaRepository<NoveltyCheck, Long> {
    List<NoveltyCheck> findByTopicLeadIdOrderByLayerNoAsc(Long topicLeadId);
}
