package com.reviewer.scout.service;

import com.reviewer.scout.entity.Expert;
import com.reviewer.scout.repository.ExpertRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ExpertService {
    private final ExpertRepository expertRepo;
    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    public ExpertService(ExpertRepository expertRepo) {
        this.expertRepo = expertRepo;
    }

    public List<Expert> extractExperts(Long scanRunId, List<com.reviewer.scout.entity.Article> articles) {
        Map<String, Expert> expertMap = new LinkedHashMap<>();
        for (var art : articles) {
            if (art.getAuthors() == null || art.getAuthors().isEmpty()) continue;
            String[] authors = art.getAuthors().split("; ");
            if (authors.length == 0) continue;

            // Use first and last author
            for (int idx : new int[]{0, authors.length - 1}) {
                String name = authors[idx].trim();
                if (name.isEmpty() || name.length() < 4) continue;
                String key = name.toLowerCase().replaceAll("[^a-z\\u4e00-\\u9fff]", "");
                if (key.length() < 4) continue;

                Expert expert = expertMap.computeIfAbsent(key, k -> {
                    Expert e = new Expert();
                    e.setScanRunId(scanRunId);
                    e.setName(name);
                    e.setStatus("PENDING");
                    e.setRisingStar(false);
                    e.setTopicOutputs36m(0);
                    e.setTotalScore(0.0);
                    return e;
                });
                expert.setTopicOutputs36m(expert.getTopicOutputs36m() + 1);

                // Score: output + recency
                double score = Math.min(expert.getTopicOutputs36m() * 1.5, 5.0);
                if (art.getPubtypes() != null && art.getPubtypes().contains("Randomized Controlled Trial")) score += 2;
                if (art.getJournal() != null && (art.getJournal().contains("Lancet") ||
                    art.getJournal().contains("JAMA") || art.getJournal().contains("New England"))) score += 3;
                expert.setTotalScore(Math.min(score, 11));

                if (idx == authors.length - 1 && expert.getRoles() == null) {
                    expert.setRoles("通讯/末位作者");
                }
            }
        }
        // Extract emails from article abstracts
        for (var art : articles) {
            String text = (art.getAbstractText() != null ? art.getAbstractText() : "") + " " +
                          (art.getAuthors() != null ? art.getAuthors() : "");
            Matcher m = EMAIL_PATTERN.matcher(text);
            if (m.find()) {
                String email = m.group();
                // Try to associate with closest author name
                for (Expert e : expertMap.values()) {
                    String lastName = e.getName().contains(" ") ?
                        e.getName().substring(e.getName().lastIndexOf(' ') + 1) : e.getName();
                    if (email.toLowerCase().contains(lastName.toLowerCase().substring(0, Math.min(3, lastName.length())))) {
                        if (e.getEmail() == null || e.getEmail().isEmpty()) {
                            e.setEmail(email);
                            e.setEmailType("A");
                            e.setEmailConfidence("A");
                            e.setEmailSourceUrl(art.getSourceUrl());
                        }
                        break;
                    }
                }
            }
        }

        List<Expert> experts = new ArrayList<>(expertMap.values());
        experts.sort((a, b) -> Double.compare(b.getTotalScore(), a.getTotalScore()));
        List<Expert> result = new ArrayList<>();
        for (int i = 0; i < Math.min(experts.size(), 20); i++) {
            Expert e = experts.get(i);
            if (e.getTopicOutputs36m() >= 2) {
                e.setRisingStar(i >= 10 && e.getTopicOutputs36m() >= 3);
                result.add(expertRepo.save(e));
            }
        }
        log.info("Extracted {} experts from {} articles", result.size(), articles.size());
        return result;
    }

    public void setStatus(Long expertId, String status) {
        expertRepo.findById(expertId).ifPresent(e -> {
            e.setStatus(status);
            expertRepo.save(e);
        });
    }
}
