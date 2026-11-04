package com.reviewer.scout.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.reviewer.scout.entity.Article;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Reproducible metadata signals. These are deliberately not evidence-quality assessments. */
public class EvidenceBuilder {
    private final ObjectMapper json = new ObjectMapper();
    private static final Set<String> GENERIC = Set.of("humans", "animals", "male", "female", "adult", "aged", "middle aged", "young adult", "adolescent", "child", "mice", "rats", "pregnancy", "prospective studies", "retrospective studies", "randomized controlled trials as topic", "follow-up studies", "treatment outcome", "research design");
    private static final Set<String> STOP = Set.of("the", "and", "for", "with", "from", "into", "between", "among", "using", "study", "studies", "review", "systematic", "analysis", "trial", "trials", "randomized", "controlled", "patients", "effects", "effect", "based", "versus", "clinical", "new", "an", "of", "in", "on", "to", "a", "is", "are", "as", "by", "or", "at");
    public static boolean review(Article a) { return Pattern.compile("(?i)(^|[|;])\\s*(review|systematic review|meta-analysis|scoping review)\\s*($|[|;])").matcher(Objects.toString(a.getPubtypes(), "")).find(); }
    public static boolean excluded(Article a) { return Boolean.TRUE.equals(a.getIsRetracted()) || Objects.toString(a.getPubtypes(), "").toLowerCase(Locale.ROOT).contains("retracted publication"); }
    public static List<Article> eligible(List<Article> all) { return all.stream().filter(a -> !excluded(a) && a.getPmid() != null).collect(Collectors.toMap(Article::getPmid, a -> a, (a,b) -> a, LinkedHashMap::new)).values().stream().toList(); }
    public ObjectNode build(List<Article> all) {
        List<Article> papers = eligible(all); ObjectNode root = json.createObjectNode();
        root.put("schemaVersion", 1); root.put("mode", "RULES"); root.put("analyzedCount", papers.size());
        root.put("excludedCount", all.stream().filter(EvidenceBuilder::excluded).count());
        root.put("missingAbstractCount", papers.stream().filter(a -> a.getAbstractText() == null || a.getAbstractText().isBlank()).count());
        ArrayNode sources = root.putArray("sources"), reviews = root.putArray("reviews");
        Map<String,Integer> months = new TreeMap<>(); int undated = 0;
        for (Article a : papers) {
            ObjectNode source = sources.addObject(); source.put("id", a.getId()); source.put("pmid", a.getPmid());
            source.put("title", Objects.toString(a.getTitle(), "")); source.put("authors", Objects.toString(a.getAuthors(), ""));
            source.put("pubdate", Objects.toString(a.getPubdate(), "")); source.put("pubtypes", Objects.toString(a.getPubtypes(), ""));
            source.put("isReview", review(a)); source.put("hasAbstract", a.getAbstractText() != null && !a.getAbstractText().isBlank());
            source.put("url", "https://pubmed.ncbi.nlm.nih.gov/" + a.getPmid() + "/");
            source.set("emailContacts", json.valueToTree(a.getEmailContacts()));
            if (review(a)) reviews.add(a.getPmid());
            String date = Objects.toString(a.getPubdate(), "");
            if (date.matches("\\d{4}-(0[1-9]|1[0-2]).*")) months.merge(date.substring(0,7),1,Integer::sum); else undated++;
        }
        root.put("undatedCount", undated); ArrayNode timeline = root.putArray("timeline");
        months.forEach((m,n) -> timeline.addObject().put("month",m).put("count",n));
        Map<String,Integer> terms = new HashMap<>();
        for (Article a : papers) Arrays.stream(Objects.toString(a.getMeshTerms(), "").split("\\|")).map(String::strip).filter(t -> t.length() > 2 && !GENERIC.contains(t.toLowerCase(Locale.ROOT))).distinct().forEach(t -> terms.merge(t,1,Integer::sum));
        boolean mesh = !terms.isEmpty();
        if (!mesh) for (Article a : papers) {
            String[] words = Objects.toString(a.getTitle(), "").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N} -]", " ").split("\\s+");
            Set<String> seen = new HashSet<>();
            for (int i=0; i+1<words.length; i++) if (words[i].length()>2 && words[i+1].length()>2 && !STOP.contains(words[i]) && !STOP.contains(words[i+1])) seen.add(words[i]+" "+words[i+1]);
            seen.forEach(t -> terms.merge(t,1,Integer::sum));
        }
        ArrayNode topics = root.putArray("topics"); Set<String> assigned = new HashSet<>();
        for (String term : terms.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey())).limit(10).map(Map.Entry::getKey).toList()) {
            ObjectNode topic = topics.addObject(); topic.put("id", "t" + topics.size()); topic.put("name",term);
            topic.put("description",mesh ? "按 MeSH 与标题摘要匹配形成的候选方向，主题边界待编辑核实。" : "按标题短语形成的候选方向，主题边界待编辑核实。");
            topic.put("maturity", "UNASSESSED"); topic.put("editorStatus", "DRAFT"); topic.put("editorNote", "");
            topic.putArray("terms").add(term); ArrayNode ids = topic.putArray("pmids"), assignments = topic.putArray("assignments");
            for (Article a : papers) {
                boolean exact = Arrays.asList(Objects.toString(a.getMeshTerms(), "").split("\\|")).stream().anyMatch(t -> t.strip().equalsIgnoreCase(term));
                boolean text = (Objects.toString(a.getTitle(), "")+" "+Objects.toString(a.getAbstractText(), "")).toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT));
                if (exact || text) { ids.add(a.getPmid()); assigned.add(a.getPmid()); assignments.addObject().put("pmid",a.getPmid()).put("basis", (exact ? "MeSH：" : "标题/摘要短语：")+term); }
            }
            topic.putArray("notes"); ArrayNode coverage = topic.putArray("coverage");
            reviews.forEach(r -> coverage.addObject().put("reviewPmid",r.asText()).put("status","UNVERIFIED").put("rationale","尚未核实正文覆盖程度").putArray("citations"));
        }
        if (topics.isEmpty() && !papers.isEmpty()) {
            ObjectNode t = topics.addObject(); t.put("id","t1"); t.put("name","待梳理文献"); t.put("description","未提取到稳定主题词，可通过 AI 辅助归类或编辑主题名称继续梳理。");
            t.put("maturity","UNASSESSED"); t.put("editorStatus","DRAFT"); t.put("editorNote",""); t.putArray("terms");
            ArrayNode ids = t.putArray("pmids"); papers.forEach(a -> ids.add(a.getPmid()));
            t.putArray("assignments"); t.putArray("notes"); t.putArray("coverage");
        }
        root.put("unassignedCount", papers.size() - assigned.size()); root.putArray("candidates");
        ArrayNode inventory=root.putArray("reviewInventory");reviews.forEach(p->inventory.addObject().put("pmid",p.asText()).put("searchCutoff","").put("scopeNote","").put("limitations","").put("editorStatus","DRAFT"));
        return root;
    }
}
