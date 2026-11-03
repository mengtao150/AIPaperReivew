package com.reviewer.scout.service;

import com.reviewer.scout.repository.ArticleRepository;
import com.reviewer.scout.entity.Article;
import com.reviewer.scout.util.PublicationEmails;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ArticleContactService {
    private final ArticleRepository articles;
    private final PubMedService pubmed;
    public ArticleContactService(ArticleRepository articles, PubMedService pubmed) { this.articles = articles; this.pubmed = pubmed; }
    public Map<String, Object> refresh(Long scanId, List<Long> ids) {
        if (scanId == null || ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(id -> id == null || id < 1)) throw new IllegalArgumentException("请选择本页的 1–100 篇文献补查邮箱");
        List<Long> uniqueIds = ids.stream().distinct().toList();
        List<Article> existing = articles.findAllById(uniqueIds);
        if (existing.size() != uniqueIds.size() || existing.stream().anyMatch(a -> !scanId.equals(a.getScanRunId()))) throw new IllegalArgumentException("所选文献不属于当前检索，请刷新后重试");
        Map<String, Article> byPmid = new LinkedHashMap<>(); existing.forEach(a -> byPmid.put(a.getPmid(), a));
        List<Article> fresh;
        try { fresh = pubmed.fetchArticles(new ArrayList<>(byPmid.keySet())); }
        catch (Exception e) { throw new IllegalStateException("PubMed 邮箱补查失败，请稍后重试"); }
        List<Article> checked = new ArrayList<>();
        for (Article item : fresh) {
            Article original = byPmid.remove(item.getPmid()); if (original == null) continue;
            original.setEmailContacts(PublicationEmails.merge(original.getEmailContacts(), item.getEmailContacts()));
            original.setEmailCheckedAt(LocalDateTime.now()); checked.add(original);
        }
        if (!checked.isEmpty()) articles.saveAll(checked);
        int found = (int) checked.stream().filter(a -> !a.getEmailContacts().isEmpty()).count();
        return Map.of("checkedCount", checked.size(), "foundCount", found, "missingCount", checked.size() - found, "emailCount", checked.stream().mapToInt(a -> a.getEmailContacts().size()).sum(), "unavailableCount", uniqueIds.size() - checked.size());
    }
}
