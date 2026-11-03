package com.reviewer.scout.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class TaskProgressService {

    private final Map<Long, TaskProgress> tasks = new ConcurrentHashMap<>();

    @Data
    public static class TaskProgress {
        private Long scanRunId;
        private String runId;
        private String topic;
        private String phase;       // CREATED, COUNTING, COUNTED, FETCHING, PAUSED, CLASSIFYING, MINING, DONE, FAILED, CANCELLED
        private String message;
        private int currentBatch;
        private int totalBatches;
        private int articlesFetched;
        private int articlesClassified;
        private double progressPercent;
        private LocalDateTime startedAt;
        private LocalDateTime updatedAt;
        private String error;
        private transient volatile boolean paused = false;
        private transient volatile boolean cancelled = false;
    }

    public TaskProgress init(Long scanRunId, String runId, String topic) {
        TaskProgress tp = new TaskProgress();
        tp.setScanRunId(scanRunId);
        tp.setRunId(runId);
        tp.setTopic(topic);
        tp.setPhase("CREATED");
        tp.setMessage("任务已创建");
        tp.setProgressPercent(0);
        tp.setStartedAt(LocalDateTime.now());
        tp.setUpdatedAt(LocalDateTime.now());
        tasks.put(scanRunId, tp);
        return tp;
    }

    public void pause(Long scanRunId) {
        TaskProgress tp = tasks.get(scanRunId);
        if (tp != null && !tp.isCancelled()) {
            tp.setPaused(true);
            tp.setPhase("PAUSED");
            tp.setMessage("已暂停 · 点击恢复继续");
            tp.setUpdatedAt(LocalDateTime.now());
            log.info("Task {} PAUSED at batch {}/{}", scanRunId, tp.getCurrentBatch(), tp.getTotalBatches());
        }
    }

    public void resume(Long scanRunId) {
        TaskProgress tp = tasks.get(scanRunId);
        if (tp != null && tp.isPaused()) {
            tp.setPaused(false);
            tp.setPhase("FETCHING");
            tp.setMessage("恢复抓取…");
            tp.setUpdatedAt(LocalDateTime.now());
            synchronized (tp) { tp.notifyAll(); }
            log.info("Task {} RESUMED", scanRunId);
        }
    }

    public void cancel(Long scanRunId) {
        TaskProgress tp = tasks.get(scanRunId);
        if (tp != null) {
            tp.setCancelled(true);
            tp.setPaused(false);
            synchronized (tp) { tp.notifyAll(); }
            tp.setPhase("CANCELLING");
            tp.setMessage("正在取消…");
            tp.setUpdatedAt(LocalDateTime.now());
            log.info("Task {} CANCEL requested", scanRunId);
        }
    }

    /** Blocks if paused; throws RuntimeException if cancelled. Call between batches. */
    public void checkControl(Long scanRunId) {
        TaskProgress tp = tasks.get(scanRunId);
        if (tp == null) return;
        if (tp.isCancelled()) {
            throw new RuntimeException("TASK_CANCELLED");
        }
        while (tp.isPaused()) {
            try {
                synchronized (tp) { tp.wait(1000); }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("TASK_INTERRUPTED");
            }
            if (tp.isCancelled()) {
                throw new RuntimeException("TASK_CANCELLED");
            }
        }
    }

    public void updatePhase(Long scanRunId, String phase, String message) {
        TaskProgress tp = tasks.get(scanRunId);
        if (tp != null) {
            tp.setPhase(phase);
            tp.setMessage(message);
            tp.setUpdatedAt(LocalDateTime.now());
            log.info("Task {} [{}] {}", scanRunId, phase, message);
        }
    }

    public void updateBatchProgress(Long scanRunId, int currentBatch, int totalBatches, int articlesFetched) {
        TaskProgress tp = tasks.get(scanRunId);
        if (tp != null) {
            tp.setCurrentBatch(currentBatch);
            tp.setTotalBatches(totalBatches);
            tp.setArticlesFetched(articlesFetched);
            tp.setProgressPercent(totalBatches > 0 ? (double) currentBatch / totalBatches * 100 : 0);
            tp.setMessage(String.format("抓取第 %d/%d 批 · 已获取 %d 篇", currentBatch, totalBatches, articlesFetched));
            tp.setUpdatedAt(LocalDateTime.now());
        }
    }

    public void updateClassifyProgress(Long scanRunId, int classified, int total) {
        TaskProgress tp = tasks.get(scanRunId);
        if (tp != null) {
            tp.setPhase("CLASSIFYING");
            tp.setArticlesClassified(classified);
            tp.setProgressPercent(total > 0 ? (double) classified / total * 100 : 0);
            tp.setMessage(String.format("分类中 %d/%d", classified, total));
            tp.setUpdatedAt(LocalDateTime.now());
        }
    }

    public void complete(Long scanRunId, String message) {
        TaskProgress tp = tasks.get(scanRunId);
        if (tp != null) {
            tp.setPhase("DONE");
            tp.setMessage(message);
            tp.setProgressPercent(100);
            tp.setUpdatedAt(LocalDateTime.now());
        }
    }

    public void fail(Long scanRunId, String error) {
        TaskProgress tp = tasks.get(scanRunId);
        if (tp != null) {
            tp.setPhase("FAILED");
            tp.setError(error);
            tp.setMessage("失败: " + error);
            tp.setUpdatedAt(LocalDateTime.now());
            log.error("Task {} FAILED: {}", scanRunId, error);
        }
    }

    public TaskProgress get(Long scanRunId) {
        return tasks.get(scanRunId);
    }

    public List<TaskProgress> getAll() {
        List<TaskProgress> list = new ArrayList<>(tasks.values());
        list.sort((a, b) -> b.getStartedAt().compareTo(a.getStartedAt()));
        return list;
    }

    public void remove(Long scanRunId) {
        tasks.remove(scanRunId);
    }
}
