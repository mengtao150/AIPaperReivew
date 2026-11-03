package com.reviewer.scout.repository;

import com.reviewer.scout.entity.ArticleCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ArticleCategoryRepository extends JpaRepository<ArticleCategory, Long> {
    List<ArticleCategory> findByCategoryIdAndIsPrimaryTrue(Long categoryId);
    List<ArticleCategory> findByArticleId(Long articleId);
}
