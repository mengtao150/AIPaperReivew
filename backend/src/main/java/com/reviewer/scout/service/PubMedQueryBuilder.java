package com.reviewer.scout.service;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class PubMedQueryBuilder {
    public String build(SearchCriteria c) {
        String query = c.getQuery() == null ? "" : c.getQuery().trim();
        if (query.isEmpty() || query.length() > 20000) throw new IllegalArgumentException("请输入检索式（最多 20,000 字符）");
        int depth = 0; boolean quoted = false;
        for (char ch : query.toCharArray()) {
            if (ch == '"') quoted = !quoted;
            if (!quoted && ch == '(') depth++;
            if (!quoted && ch == ')' && --depth < 0) throw new IllegalArgumentException("检索式括号不匹配");
        }
        if (quoted || depth != 0) throw new IllegalArgumentException("请检查检索式中的引号和括号");
        if (query.matches("(?s).*#[0-9]+.*")) throw new IllegalArgumentException("请将 PubMed 历史编号展开为完整检索式后粘贴");
        if (!Set.of("dp", "edat", "crdt", "epdat", "ppdat").contains(c.getDateField())) throw new IllegalArgumentException("不支持的日期字段");
        if (!Set.of("relevance", "pub_date").contains(c.getSortOrder())) throw new IllegalArgumentException("不支持的排序方式");
        if (c.getDateFrom() != null && c.getDateTo() != null && c.getDateFrom().isAfter(c.getDateTo())) throw new IllegalArgumentException("起始日期不能晚于结束日期");
        if ((c.getDateFrom() == null) != (c.getDateTo() == null)) throw new IllegalArgumentException("请同时填写起止日期，或清空日期限制");
        List<String> terms = new ArrayList<>(); terms.add("(" + query + ")");
        if (c.getArticleTypes() != null && !c.getArticleTypes().isBlank()) {
            List<String> types = new ArrayList<>();
            for (String t : c.getArticleTypes().split("\\|")) {
                t = t.trim();
                if (!t.matches("[a-zA-Z0-9 ,.-]{1,100}")) throw new IllegalArgumentException("文章类型格式不正确");
                types.add(t.equalsIgnoreCase("systematic review") ? "systematic[sb]" : "\"" + t + "\"[pt]");
            }
            terms.add("(" + String.join(" OR ", types) + ")");
        }
        if (c.getLanguage() != null && !c.getLanguage().isBlank()) {
            if (!c.getLanguage().matches("[a-zA-Z]{2,20}")) throw new IllegalArgumentException("语言条件格式不正确");
            terms.add(c.getLanguage() + "[la]");
        }
        switch (c.getTextAvailability()) {
            case "any" -> { }
            case "abstract" -> terms.add("hasabstract");
            case "fulltext" -> terms.add("full text[sb]");
            case "freefulltext" -> terms.add("free full text[sb]");
            default -> throw new IllegalArgumentException("不支持的文本可用性条件");
        }
        if (c.getDateFrom() != null) terms.add("(\"" + c.getDateFrom().toString().replace('-', '/') + "\"[" + c.getDateField() + "] : \"" + c.getDateTo().toString().replace('-', '/') + "\"[" + c.getDateField() + "])");
        return String.join(" AND ", terms);
    }
}
