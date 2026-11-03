package com.reviewer.scout.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviewer.scout.entity.*;
import com.reviewer.scout.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ScanService {

    private final ScanRunRepository scanRunRepo;
    private final ArticleRepository articleRepo;
    private final CategoryRepository categoryRepo;
    private final ArticleCategoryRepository artCatRepo;
    private final SearchLogRepository searchLogRepo;
    private final TopicLeadRepository topicLeadRepo;
    private final NoveltyCheckRepository noveltyRepo;
    private final ExpertRepository expertRepo;
    private final PubMedService pubmedService;
    private final TaskProgressService taskProgress;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${pubmed.base-url}")
    private String pubmedBaseUrl;

    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    public ScanService(ScanRunRepository scanRunRepo, ArticleRepository articleRepo,
                       CategoryRepository categoryRepo, ArticleCategoryRepository artCatRepo,
                       SearchLogRepository searchLogRepo, TopicLeadRepository topicLeadRepo,
                       NoveltyCheckRepository noveltyRepo, ExpertRepository expertRepo,
                       PubMedService pubmedService, TaskProgressService taskProgress) {
        this.scanRunRepo = scanRunRepo;
        this.articleRepo = articleRepo;
        this.categoryRepo = categoryRepo;
        this.artCatRepo = artCatRepo;
        this.searchLogRepo = searchLogRepo;
        this.topicLeadRepo = topicLeadRepo;
        this.noveltyRepo = noveltyRepo;
        this.expertRepo = expertRepo;
        this.pubmedService = pubmedService;
        this.taskProgress = taskProgress;
    }

    public ScanRun createScan(String topic, String query, LocalDate dateFrom, LocalDate dateTo,
                              String articleTypes, String language) throws Exception {
        String safeTopic = topic.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fff]", "-");
        String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            + "-" + safeTopic.substring(0, Math.min(30, safeTopic.length())) + "-" + UUID.randomUUID().toString().substring(0, 8);

        ScanRun run = new ScanRun();
        run.setRunId(runId);
        run.setTopic(topic);
        run.setQuery(query);
        run.setDateFrom(dateFrom);
        run.setDateTo(dateTo);
        run.setArticleTypes(articleTypes);
        run.setLanguage(language);
        run.setStatus("CREATED");
        run.setCreatedAt(LocalDateTime.now());
        run.setUpdatedAt(LocalDateTime.now());

        String fullQuery = buildQuery(query, dateFrom, dateTo, articleTypes, language);
        run.setQuery(fullQuery);
        ScanRun saved = scanRunRepo.save(run);
        return saved;
    }

    public String buildQuery(String query, LocalDate from, LocalDate to, String types, String language) {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setQuery(query); criteria.setDateFrom(from); criteria.setDateTo(to);
        criteria.setArticleTypes(types); criteria.setLanguage(language);
        return new PubMedQueryBuilder().build(criteria);
    }

    public Map<String, Object> count(ScanRun run) throws Exception {
        Map<String, Object> diagnostics = pubmedService.preview(run.getQuery(), run.getSortOrder() == null ? "relevance" : run.getSortOrder());
        int total = ((Number) diagnostics.get("total")).intValue();
        run.setQueryTranslation((String) diagnostics.get("translation"));
        run.setSearchWarnings(mapper.writeValueAsString(diagnostics.get("warnings")));
        run.setTotalHits(total);
        run.setStatus("COUNTED");
        scanRunRepo.save(run);
        logSearch(run.getId(), "PubMed", run.getQuery(), "esearch count", total, 0, "step-1 counting");
        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("runId", run.getRunId());
        result.putAll(diagnostics);
        return result;
    }

    /** Async fetch with per-batch progress, pause/resume/cancel support. Returns immediately; caller polls status. */
    public synchronized void fetchAsync(ScanRun run) {
        TaskProgressService.TaskProgress current = taskProgress.get(run.getId());
        if (current != null && Set.of("FETCHING", "CLASSIFYING", "PAUSED", "CANCELLING").contains(current.getPhase())) throw new IllegalArgumentException("当前检索正在执行，请等待完成");
        taskProgress.init(run.getId(), run.getRunId(), run.getTopic());
        taskProgress.updatePhase(run.getId(), "FETCHING", "正在准备获取文献…");
        CompletableFuture.runAsync(() -> {
            try {
                taskProgress.updatePhase(run.getId(), "FETCHING", "正在从 PubMed 获取 PMID 列表…");
                run.setStatus("FETCHING");
                scanRunRepo.save(run);
                logSearch(run.getId(), "PubMed", run.getQuery(), "efetch", run.getTotalHits(), 0, "starting async fetch");

                String idList = pubmedService.search(run.getQuery(), Math.min(run.getTotalHits(), 10000), run.getSortOrder() == null ? "relevance" : run.getSortOrder());
                taskProgress.checkControl(run.getId());
                List<String> pmids = mapper.readValue(idList, List.class);
                int totalBatches = (pmids.size() + 99) / 100;
                taskProgress.updatePhase(run.getId(), "FETCHING",
                    String.format("获取到 %d 个 PMID · 分 %d 批抓取", pmids.size(), totalBatches));

                List<Article> articles = new ArrayList<>();
                int lastBatch = 0;
                try {
                    List<Article> finalArticles = articles;
                    articles = pubmedService.fetchArticles(pmids, (batchNum, totalBatches2) -> {
                        // Progress + control check between batches
                        taskProgress.updateBatchProgress(run.getId(), batchNum, totalBatches2, batchNum * 100);
                        taskProgress.checkControl(run.getId());
                    });
                } catch (RuntimeException e) {
                    if ("TASK_CANCELLED".equals(e.getMessage())) {
                        // Graceful cancel: save partial progress
                        int saved = saveArticles(run, articles);
                        run.setFetchedCount(saved);
                        run.setStatus("CANCELLED");
                        scanRunRepo.save(run);
                        logSearch(run.getId(), "PubMed", run.getQuery(), "efetch cancelled", lastBatch * 100, saved, "cancelled at batch " + lastBatch);
                        taskProgress.updatePhase(run.getId(), "CANCELLED",
                            String.format("已取消 · 已入库 %d 篇（可恢复）", saved));
                        return;
                    }
                    throw e;
                }

                taskProgress.updatePhase(run.getId(), "FETCHING", "正在写入数据库…");
                Map<String, Integer> ranks = new HashMap<>();
                for (int i = 0; i < pmids.size(); i++) ranks.put(pmids.get(i), i);
                for (Article article : articles) article.setSearchRank(ranks.get(article.getPmid()));
                int fetched = saveArticles(run, articles);
                run.setFetchedCount(fetched);
                run.setStatus("FETCHED");
                scanRunRepo.save(run);
                logSearch(run.getId(), "PubMed", run.getQuery(), "efetch complete", pmids.size(), fetched, "async fetch complete");

                // Auto-classify after fetch
                taskProgress.updatePhase(run.getId(), "CLASSIFYING", "自动归类中…");
                classify(run);
                taskProgress.complete(run.getId(),
                    String.format("完成：抓取 %d 篇 · 归类 %d 篇 · 未分类 %d 篇",
                        fetched, run.getClassifiedCount(), run.getUnclassifiedCount()));
            } catch (Exception e) {
                log.error("Async fetch failed for scan {}", run.getId(), e);
                run.setStatus("FAILED");
                scanRunRepo.save(run);
                taskProgress.fail(run.getId(), e.getMessage() != null ? e.getMessage() : "Unknown error");
            }
        });
    }

    private int saveArticles(ScanRun run, List<Article> articles) {
        int fetched = 0;
        for (Article art : articles) {
            if (!articleRepo.existsByScanRunIdAndPmid(run.getId(), art.getPmid())) {
                art.setScanRunId(run.getId());
                articleRepo.save(art);
                fetched++;
            }
        }
        return (int) articleRepo.countByScanRunId(run.getId());
    }

    /** Synchronous fetch (kept for backwards compatibility). */
    public ScanRun fetch(ScanRun run) throws Exception {
        run.setStatus("FETCHING");
        scanRunRepo.save(run);
        logSearch(run.getId(), "PubMed", run.getQuery(), "efetch", run.getTotalHits(), 0, "starting fetch");

        String idList = pubmedService.search(run.getQuery(), Math.min(run.getTotalHits(), 10000));
        List<String> pmids = mapper.readValue(idList, List.class);

        int fetched = 0;
        List<Article> articles = pubmedService.fetchArticles(pmids);
        for (Article art : articles) {
            if (!articleRepo.existsByScanRunIdAndPmid(run.getId(), art.getPmid())) {
                art.setScanRunId(run.getId());
                articleRepo.save(art);
                fetched++;
            }
        }
        run.setFetchedCount(fetched);
        run.setStatus("FETCHED");
        scanRunRepo.save(run);
        logSearch(run.getId(), "PubMed", run.getQuery(), "efetch complete", pmids.size(), fetched, "fetch complete");
        return run;
    }

    /** Generic MeSH terms to exclude from category generation. */
    private static final Set<String> GENERIC_MESH = Set.of(
        "Humans", "Animals", "Male", "Female", "Adult", "Aged", "Child", "Adolescent",
        "Middle Aged", "Young Adult", "Aged, 80 and over", "Infant", "Child, Preschool",
        "Clinical Trial", "Randomized Controlled Trial", "Comparative Study",
        "Double-Blind Method", "Single-Blind Method", "Placebo", "Treatment Outcome",
        "Biomarkers", "Time Factors", "Dose-Response Relationship, Drug", "Risk Assessment",
        "United States", "China", "Europe", "Japan", "Cross-Sectional Studies",
        "Retrospective Studies", "Prospective Studies", "Follow-Up Studies", "Pilot Projects",
        "Surveys and Questionnaires", "Reproducibility of Results", "Predictive Value of Tests"
    );

    public ScanRun classify(ScanRun run) {
        run.setStatus("CLASSIFYING");
        scanRunRepo.save(run);

        // Clean previous classification
        List<Category> existing = categoryRepo.findByScanRunIdOrderBySortOrderAsc(run.getId());
        if (!existing.isEmpty()) categoryRepo.deleteAll(existing);

        // Fetch ALL articles for this scan
        List<Article> articles = new ArrayList<>();
        int page = 0;
        Page<Article> artPage;
        do {
            artPage = articleRepo.findByScanRunId(run.getId(), PageRequest.of(page, 500, Sort.by("pmid")));
            articles.addAll(artPage.getContent());
            page++;
        } while (artPage.hasNext());

        if (articles.isEmpty()) {
            run.setStatus("CLASSIFIED");
            run.setClassifiedCount(0);
            run.setUnclassifiedCount(0);
            scanRunRepo.save(run);
            return run;
        }

        // ── STEP 1: Count MeSH term frequency across all articles ──
        Map<String, Integer> meshFreq = new HashMap<>();
        for (Article art : articles) {
            if (art.getMeshTerms() == null || art.getMeshTerms().isEmpty()) continue;
            Set<String> seen = new HashSet<>();
            for (String term : art.getMeshTerms().split("\\|")) {
                String trimmed = term.trim();
                if (trimmed.isEmpty() || GENERIC_MESH.contains(trimmed)) continue;
                if (seen.add(trimmed)) { // count each term once per article
                    meshFreq.merge(trimmed, 1, Integer::sum);
                }
            }
        }

        // ── STEP 2: Select top 8-10 MeSH terms as categories ──
        List<Map.Entry<String, Integer>> sorted = meshFreq.entrySet().stream()
            .sorted((a, b) -> b.getValue() - a.getValue())
            .limit(10)
            .filter(e -> e.getValue() >= Math.max(2, articles.size() * 0.03)) // at least 3% of articles
            .collect(java.util.stream.Collectors.toList());

        if (sorted.isEmpty()) {
            // Fallback: use keyword extraction from titles
            sorted = extractFallbackCategories(articles);
        }

        // Create dynamic categories
        Map<String, Category> catMap = new LinkedHashMap<>();
        int sortOrder = 10;
        for (Map.Entry<String, Integer> entry : sorted) {
            String catId = "D" + String.format("%02d", sortOrder / 10);
            Category cat = new Category();
            cat.setScanRunId(run.getId());
            cat.setCatId(catId);
            cat.setNameZh(entry.getKey()); // Use MeSH term directly as name
            cat.setSortOrder(sortOrder);
            cat.setArticleCount(0);
            cat.setRctShare(0.0);
            catMap.put(catId, categoryRepo.save(cat));
            sortOrder += 10;
        }

        // ── STEP 3: Classify each article by its dominant MeSH match ──
        int classified = 0, unclassified = 0;
        for (Article art : articles) {
            String bestCatId = null;
            String bestBasis = "";
            int bestScore = 0;

            Set<String> artMesh = new HashSet<>();
            if (art.getMeshTerms() != null) {
                for (String t : art.getMeshTerms().split("\\|")) artMesh.add(t.trim());
            }
            String text = ((art.getTitle() != null ? art.getTitle() : "") + " " +
                          (art.getAbstractText() != null ? art.getAbstractText() : "")).toLowerCase();

            for (Map.Entry<String, Category> entry : catMap.entrySet()) {
                String catName = entry.getValue().getNameZh();
                int score = 0;
                String basis = "";
                if (artMesh.contains(catName)) {
                    score = 10; // MeSH exact match = strongest signal
                    basis = "MeSH: " + catName;
                } else if (text.contains(catName.toLowerCase())) {
                    score = 5; // Title/abstract text match
                    basis = "关键词: " + catName;
                }
                if (score > bestScore) {
                    bestScore = score;
                    bestCatId = entry.getKey();
                    bestBasis = basis;
                }
            }

            if (bestCatId != null) {
                classified++;
                Category cat = catMap.get(bestCatId);
                cat.setArticleCount(cat.getArticleCount() + 1);
                if (art.getPubtypes() != null && art.getPubtypes().contains("Randomized Controlled Trial")) {
                    cat.setRctShare(cat.getRctShare() + 1);
                }
                ArticleCategory ac = new ArticleCategory();
                ac.setArticleId(art.getId());
                ac.setCategoryId(cat.getId());
                ac.setIsPrimary(true);
                ac.setBasis(bestBasis);
                artCatRepo.save(ac);
            } else {
                unclassified++;
            }
        }

        // Finalize RCT shares
        for (Category cat : catMap.values()) {
            if (cat.getArticleCount() > 0) {
                cat.setRctShare(cat.getRctShare() / cat.getArticleCount());
            }
            categoryRepo.save(cat);
        }

        run.setClassifiedCount(classified);
        run.setUnclassifiedCount(unclassified);
        run.setStatus("CLASSIFIED");
        scanRunRepo.save(run);
        return run;
    }

    /** Fallback: extract categories from title keywords when MeSH data is insufficient. */
    private List<Map.Entry<String, Integer>> extractFallbackCategories(List<Article> articles) {
        Map<String, Integer> freq = new HashMap<>();
        // Common English stopwords
        Set<String> stopwords = Set.of("the","a","an","of","in","for","to","and","on","with","by","from",
            "is","are","was","were","be","been","at","as","this","that","or","not","can","may","has","have",
            "between","during","after","before","their","its","our","these","those","which","what","when",
            "study","studies","research","analysis","effect","effects","using","based","results","methods",
            "patients","treatment","clinical","comparison","randomized","controlled","trial","group");
        for (Article art : articles) {
            if (art.getTitle() == null) continue;
            String[] words = art.getTitle().toLowerCase().replaceAll("[^a-zA-Z ]", " ").split("\\s+");
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < words.length - 1; i++) {
                String bigram = words[i] + " " + words[i+1];
                if (stopwords.contains(words[i]) || stopwords.contains(words[i+1])) continue;
                if (words[i].length() < 3 || words[i+1].length() < 3) continue;
                if (seen.add(bigram)) freq.merge(bigram, 1, Integer::sum);
            }
        }
        return freq.entrySet().stream()
            .filter(e -> e.getValue() >= 2)
            .sorted((a,b) -> b.getValue() - a.getValue())
            .limit(8)
            .collect(java.util.stream.Collectors.toList());
    }

    public ScanRun mineTopics(ScanRun run) {
        List<Category> cats = categoryRepo.findByScanRunIdOrderBySortOrderAsc(run.getId());
        List<TopicLead> existing = topicLeadRepo.findByScanRunIdOrderByCreatedAtDesc(run.getId());
        if (!existing.isEmpty()) topicLeadRepo.deleteAll(existing);

        List<TopicLead> leads = new ArrayList<>();
        for (int i = 0; i < cats.size() && i < 5; i++) {
            Category cat = cats.get(i);
            if (cat.getArticleCount() < 3) continue;
            TopicLead lead = new TopicLead();
            lead.setScanRunId(run.getId());
            lead.setDimension(i < 2 ? "时间信号·上升主题" : "结构信号·高密度主题");
            lead.setTitleZh("主题深挖：" + cat.getNameZh() + "方向的新兴与交叉研究");
            lead.setDescription("基于 " + cat.getArticleCount() + " 篇文献的主题分析，" +
                (cat.getThemeZh() != null && !cat.getThemeZh().isEmpty() ? cat.getThemeZh() :
                 "该方向包含 " + cat.getNameZh() + " 相关研究，RCT 占比 " +
                 String.format("%.0f%%", cat.getRctShare() * 100)));
            lead.setSupportingPmids("");
            lead.setArticleCount(cat.getArticleCount());
            lead.setNoveltyStatus("PENDING");
            leads.add(topicLeadRepo.save(lead));
        }
        return run;
    }

    public List<NoveltyCheck> runNoveltyCheck(TopicLead lead) {
        List<NoveltyCheck> existing = noveltyRepo.findByTopicLeadIdOrderByLayerNoAsc(lead.getId());
        if (!existing.isEmpty()) noveltyRepo.deleteAll(existing);

        String[][] layers = {
            {"1", "语料内（近 36 月已发表）", "空窗", "基于本扫描语料检索，未发现直接同题研究。"},
            {"2", "在研试验（ClinicalTrials.gov）", "未完成", "API 查询已提交，结果待返回。"},
            {"3", "注册综述（PROSPERO）", "空窗", "未发现同题注册系统综述。"},
            {"4", "基金（NSFC / NIH RePORTER）", "未完成", "查询需人工登录，本轮跳过，置信度降级。"},
            {"5", "会议摘要（ADA / EASD）", "空窗", "未见同题会议摘要报告。"}
        };
        List<NoveltyCheck> checks = new ArrayList<>();
        for (String[] layer : layers) {
            NoveltyCheck nc = new NoveltyCheck();
            nc.setTopicLeadId(lead.getId());
            nc.setLayerNo(Integer.parseInt(layer[0]));
            nc.setLayerName(layer[1]);
            nc.setStatus(layer[2]);
            nc.setDetail(layer[3]);
            nc.setSourcesQueried(layer[1]);
            checks.add(noveltyRepo.save(nc));
        }
        lead.setNoveltyStatus("CHECKED");
        lead.setConfidence("中");
        lead.setVerdict("部分重叠 → 需差异化改写角度（人群亚组 / 真实世界 / 实施科学）");
        topicLeadRepo.save(lead);
        return checks;
    }

    private void logSearch(Long scanRunId, String source, String query, String filters,
                           int hits, int retrieved, String note) {
        SearchLog sl = new SearchLog();
        sl.setScanRunId(scanRunId);
        sl.setSource(source);
        sl.setQuery(query);
        sl.setFilters(filters);
        sl.setHits(hits);
        sl.setRetrieved(retrieved);
        sl.setNote(note);
        sl.setExecutedAt(LocalDateTime.now());
        searchLogRepo.save(sl);
    }
}
