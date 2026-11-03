package com.reviewer.scout.controller;

import com.reviewer.scout.entity.*;
import com.reviewer.scout.repository.*;
import com.reviewer.scout.service.*;
import com.reviewer.scout.service.TaskProgressService.TaskProgress;
import lombok.Data;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/scans")
public class ScanController {

    private final ScanService scanService;
    private final ScanRunRepository scanRunRepo;
    private final ArticleRepository articleRepo;
    private final CategoryRepository categoryRepo;
    private final ArticleCategoryRepository artCatRepo;
    private final TopicLeadRepository topicLeadRepo;
    private final NoveltyCheckRepository noveltyRepo;
    private final ExpertRepository expertRepo;
    private final ExpertService expertService;
    private final SearchLogRepository searchLogRepo;
    private final TaskProgressService taskProgressService;
    private final ArticleContactService articleContactService;

    public ScanController(ScanService scanService, ScanRunRepository scanRunRepo,
                          ArticleRepository articleRepo, CategoryRepository categoryRepo,
                          ArticleCategoryRepository artCatRepo, TopicLeadRepository topicLeadRepo,
                          NoveltyCheckRepository noveltyRepo, ExpertRepository expertRepo,
                          ExpertService expertService, SearchLogRepository searchLogRepo,
                          TaskProgressService taskProgressService, ArticleContactService articleContactService) {
        this.scanService = scanService;
        this.scanRunRepo = scanRunRepo;
        this.articleRepo = articleRepo;
        this.categoryRepo = categoryRepo;
        this.artCatRepo = artCatRepo;
        this.topicLeadRepo = topicLeadRepo;
        this.noveltyRepo = noveltyRepo;
        this.expertRepo = expertRepo;
        this.expertService = expertService;
        this.searchLogRepo = searchLogRepo;
        this.taskProgressService = taskProgressService;
        this.articleContactService = articleContactService;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody ScanRequest req) {
        // Query validation: must be non-empty
        if (req.getQuery() == null || req.getQuery().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error",
                "检索式不能为空。请输入 PubMed 核心检索式，例如：(\"Diabetes Mellitus\"[MeSH Terms] OR diabetes[tiab])"));
        }
        if (req.getTopic() == null || req.getTopic().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "研究主题不能为空"));
        }
        try {
            String compiled = new PubMedQueryBuilder().build(req);
            ScanRun run = scanService.createScan(req.getTopic(), req.getQuery().trim(),
                null, null, "", "");
            run.setQuery(compiled); run.setSourceQuery(req.getQuery().trim());
            run.setDateFrom(req.getDateFrom()); run.setDateTo(req.getDateTo());
            run.setArticleTypes(req.getArticleTypes()); run.setLanguage(req.getLanguage());
            run.setDateField(req.getDateField()); run.setTextAvailability(req.getTextAvailability()); run.setSortOrder(req.getSortOrder());
            scanRunRepo.save(run);
            Map<String, Object> countResult = scanService.count(run);
            taskProgressService.init(run.getId(), run.getRunId(), run.getTopic());
            taskProgressService.updatePhase(run.getId(), "COUNTED",
                String.format("计数完成：命中 %d 篇，等待确认抓取", run.getTotalHits()));
            Map<String, Object> result = new HashMap<>();
            result.put("run", run);
            result.put("count", countResult);
            result.put("query", run.getQuery());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/preview")
    public ResponseEntity<?> preview(@RequestBody ScanRequest req) throws Exception {
        String query = new PubMedQueryBuilder().build(req);
        return ResponseEntity.ok(Map.of("query", query));
    }

    @GetMapping
    public ResponseEntity<List<ScanRun>> list() {
        return ResponseEntity.ok(scanRunRepo.findAll().stream().map(run -> {
            ScanRun view = new ScanRun(); org.springframework.beans.BeanUtils.copyProperties(run, view);
            if (taskProgressService.get(run.getId()) == null && Set.of("FETCHING", "CLASSIFYING", "PAUSED", "CANCELLING").contains(run.getStatus())) view.setStatus("INTERRUPTED");
            return view;
        }).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScanRun> get(@PathVariable Long id) {
        return scanRunRepo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/fetch")
    public ResponseEntity<?> fetch(@PathVariable Long id) {
        return scanRunRepo.findById(id).map(run -> {
            try {
                scanService.fetchAsync(run);
                return ResponseEntity.ok(Map.of("status", "ASYNC_STARTED", "message", "异步抓取已启动，请轮询 /scans/" + id + "/status 获取进度"));
            } catch (Exception e) {
                return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<?> status(@PathVariable Long id) {
        TaskProgress tp = taskProgressService.get(id);
        if (tp != null) return ResponseEntity.ok(tp);
        return scanRunRepo.findById(id).map(run -> {
            boolean interrupted = Set.of("FETCHING", "CLASSIFYING", "PAUSED", "CANCELLING").contains(run.getStatus());
            return ResponseEntity.ok(Map.of("scanRunId", id, "phase", interrupted ? "INTERRUPTED" : run.getStatus(), "message", interrupted ? "上次获取因服务重启中断。可编辑检索方案后重新获取，已入库文献仍可阅读。" : "任务状态：" + run.getStatus()));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/tasks")
    public ResponseEntity<List<TaskProgress>> allTasks() {
        return ResponseEntity.ok(taskProgressService.getAll());
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<?> pause(@PathVariable Long id) {
        requireTask(id);
        taskProgressService.pause(id);
        return ResponseEntity.ok(Map.of("status", "PAUSED"));
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<?> resume(@PathVariable Long id) {
        requireTask(id);
        taskProgressService.resume(id);
        return ResponseEntity.ok(Map.of("status", "RESUMED"));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long id) {
        requireTask(id);
        taskProgressService.cancel(id);
        return ResponseEntity.ok(Map.of("status", "CANCELLING"));
    }

    private void requireTask(Long id) {
        if (taskProgressService.get(id) == null) throw new IllegalArgumentException("任务已中断或不存在，请编辑检索方案后重新开始");
    }

    @PostMapping("/{id}/classify")
    public ResponseEntity<?> classify(@PathVariable Long id) {
        return scanRunRepo.findById(id).map(run -> {
            ScanRun updated = scanService.classify(run);
            return ResponseEntity.ok(updated);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/mine")
    public ResponseEntity<?> mine(@PathVariable Long id) {
        return scanRunRepo.findById(id).map(run -> {
            scanService.mineTopics(run);
            List<TopicLead> leads = topicLeadRepo.findByScanRunIdOrderByCreatedAtDesc(id);
            return ResponseEntity.ok(leads);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/articles")
    public ResponseEntity<?> articles(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "relevance") String sort) {
        if (page < 0 || size < 1 || size > 100) return ResponseEntity.badRequest().body(Map.of("error", "分页参数不正确"));
        PageRequest pr = PageRequest.of(page, size, "pub_date".equals(sort) ? Sort.by(Sort.Direction.DESC, "pubdate").and(Sort.by("id")) : Sort.by("searchRank").and(Sort.by("id")));
        Page<Article> result;
        if (categoryId != null) {
            result = articleRepo.findByScanRunIdAndCategoryId(id, categoryId, pr);
        } else {
            result = articleRepo.findByScanRunId(id, pr);
        }
        Map<String, Object> response = new HashMap<>();
        response.put("content", result.getContent());
        response.put("totalElements", result.getTotalElements());
        response.put("totalPages", result.getTotalPages());
        response.put("number", result.getNumber());
        response.put("size", result.getSize());
        return ResponseEntity.ok(response);
    }

    public record ContactRequest(List<Long> articleIds) { }
    @PostMapping("/{id}/articles/contacts")
    public ResponseEntity<?> refreshArticleContacts(@PathVariable Long id, @RequestBody ContactRequest request) {
        return ResponseEntity.ok(articleContactService.refresh(id, request.articleIds()));
    }

    @GetMapping("/{id}/categories")
    public ResponseEntity<List<Category>> categories(@PathVariable Long id) {
        return ResponseEntity.ok(categoryRepo.findByScanRunIdOrderBySortOrderAsc(id));
    }

    @GetMapping("/{id}/leads")
    public ResponseEntity<List<TopicLead>> leads(@PathVariable Long id) {
        return ResponseEntity.ok(topicLeadRepo.findByScanRunIdOrderByCreatedAtDesc(id));
    }

    @PostMapping("/leads/{leadId}/check")
    public ResponseEntity<?> checkNovelty(@PathVariable Long leadId) {
        return topicLeadRepo.findById(leadId).map(lead -> {
            List<NoveltyCheck> checks = scanService.runNoveltyCheck(lead);
            return ResponseEntity.ok(checks);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/leads/{leadId}/checks")
    public ResponseEntity<List<NoveltyCheck>> getChecks(@PathVariable Long leadId) {
        return ResponseEntity.ok(noveltyRepo.findByTopicLeadIdOrderByLayerNoAsc(leadId));
    }

    @PostMapping("/{id}/experts")
    public ResponseEntity<?> extractExperts(@PathVariable Long id) {
        List<Article> articles = articleRepo.findByScanRunId(id, PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "pubdate"))).getContent();
        List<Expert> experts = expertService.extractExperts(id, articles);
        return ResponseEntity.ok(experts);
    }

    @GetMapping("/{id}/experts")
    public ResponseEntity<?> experts(@PathVariable Long id,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        Page<Expert> result = expertRepo.findByScanRunId(id, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "totalScore")));
        Map<String, Object> response = new HashMap<>();
        response.put("content", result.getContent());
        response.put("totalElements", result.getTotalElements());
        response.put("totalPages", result.getTotalPages());
        response.put("number", result.getNumber());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/experts/{expertId}/status")
    public ResponseEntity<?> updateExpertStatus(@PathVariable Long expertId, @RequestBody Map<String, String> body) {
        expertService.setStatus(expertId, body.get("status"));
        return ResponseEntity.ok(Map.of("status", body.get("status")));
    }

    @GetMapping("/{id}/logs")
    public ResponseEntity<List<SearchLog>> logs(@PathVariable Long id) {
        return ResponseEntity.ok(searchLogRepo.findByScanRunIdOrderByExecutedAtAsc(id));
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<?> stats(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        result.put("categories", categoryRepo.findByScanRunIdOrderBySortOrderAsc(id));
        result.put("logs", searchLogRepo.findByScanRunIdOrderByExecutedAtAsc(id));
        return ResponseEntity.ok(result);
    }

    @Data
    public static class ScanRequest extends SearchCriteria {
    }
}
