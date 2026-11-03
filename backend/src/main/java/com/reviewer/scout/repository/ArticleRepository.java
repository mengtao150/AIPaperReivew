package com.reviewer.scout.repository;

import com.reviewer.scout.entity.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArticleRepository extends JpaRepository<Article, Long> {
    Page<Article> findByScanRunId(Long scanRunId, Pageable pageable);

    @Query("SELECT a FROM Article a WHERE a.scanRunId = :scanRunId AND a.id IN " +
           "(SELECT ac.articleId FROM ArticleCategory ac WHERE ac.categoryId = :categoryId AND ac.isPrimary = true)")
    Page<Article> findByScanRunIdAndCategoryId(@Param("scanRunId") Long scanRunId,
                                                @Param("categoryId") Long categoryId,
                                                Pageable pageable);

    long countByScanRunId(Long scanRunId);
    boolean existsByScanRunIdAndPmid(Long scanRunId, String pmid);
}
