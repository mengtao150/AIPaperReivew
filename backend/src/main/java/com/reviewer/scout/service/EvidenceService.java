package com.reviewer.scout.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.reviewer.scout.entity.*;
import com.reviewer.scout.repository.*;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class EvidenceService {
    private final ArticleRepository articles; private final ScanRunRepository scans; private final EvidenceDocumentRepository documents;
    private final ModelVault vault; private final ChatGateway gateway; private final ObjectMapper mapper=new ObjectMapper();
    private final ExecutorService executor=Executors.newFixedThreadPool(2,r -> {Thread t=new Thread(r,"evidence-analysis");t.setDaemon(true);return t;});
    private final Map<String,ObjectNode> jobs=new ConcurrentHashMap<>();
    public EvidenceService(ArticleRepository articles,ScanRunRepository scans,EvidenceDocumentRepository documents,ModelVault vault,ChatGateway gateway) {
        this.articles=articles;this.scans=scans;this.documents=documents;this.vault=vault;this.gateway=gateway;
    }
    @PreDestroy public void close(){executor.shutdownNow();}
    private ScanRun scan(Long id){return scans.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"检索任务不存在"));}
    private List<Article> corpus(Long id) {
        List<Article> result=new ArrayList<>(); Page<Article> page; int n=0;
        do {page=articles.findByScanRunId(id,PageRequest.of(n++,1000,Sort.by("searchRank").and(Sort.by("id"))));result.addAll(page.getContent());}while(page.hasNext());
        return result;
    }
    private String fingerprint(List<Article> all) {
        try {MessageDigest d=MessageDigest.getInstance("SHA-256");for(Article a:all) for(String s:List.of(Objects.toString(a.getPmid(),""),Objects.toString(a.getTitle(),""),Objects.toString(a.getAbstractText(),""),Objects.toString(a.getMeshTerms(),""),Objects.toString(a.getPubtypes(),""),Objects.toString(a.getPubdate(),""),Objects.toString(a.getIsRetracted(),""),Objects.toString(a.getAuthors(),""),Objects.toString(a.getEmailContacts(),""))) {d.update(s.getBytes(StandardCharsets.UTF_8));d.update((byte)0);}return HexFormat.of().formatHex(d.digest());}
        catch(Exception e){throw new IllegalStateException("无法校验分析范围");}
    }
    private ObjectNode parse(EvidenceDocument d){try{return (ObjectNode)mapper.readTree(d.getDocument());}catch(Exception e){throw new IllegalStateException("梳理记录无法读取，请保留记录并重试");}}
    private ObjectNode baseline(List<Article> all) {ObjectNode r=new EvidenceBuilder().build(all);r.put("sourceFingerprint",fingerprint(all));r.put("generatedAt",LocalDateTime.now().toString());return r;}
    public ObjectNode read(String workspace,Long id) {
        ScanRun scan=scan(id);List<Article> all=corpus(id);Optional<EvidenceDocument> saved=documents.findByWorkspaceAndScanRunId(workspace,id);
        ObjectNode r=saved.map(this::parse).orElseGet(()->baseline(all));ObjectNode result=mapper.createObjectNode();
        result.put("revision",saved.map(EvidenceDocument::getRevision).orElse(0L)); result.put("persisted",saved.isPresent());
        result.put("stale",!r.path("sourceFingerprint").asText().equals(fingerprint(all)));result.set("report",r);
        ObjectNode scope=result.putObject("scope");scope.put("topic",scan.getTopic());scope.put("query",scan.getQuery());scope.put("retrievedCount",all.size());
        scope.put("totalHits",scan.getTotalHits()==null?all.size():scan.getTotalHits());scope.put("dateFrom",Objects.toString(scan.getDateFrom(),"不限"));scope.put("dateTo",Objects.toString(scan.getDateTo(),"不限"));
        scope.put("language",Objects.toString(scan.getLanguage(),""));scope.put("articleTypes",Objects.toString(scan.getArticleTypes(),""));
        result.set("job",job(workspace,id));return result;
    }
    public ObjectNode job(String workspace,Long id){return jobs.getOrDefault(workspace+":"+id,mapper.createObjectNode().put("status","IDLE")).deepCopy();}
    private long revision(String workspace,Long id){return documents.findByWorkspaceAndScanRunId(workspace,id).map(EvidenceDocument::getRevision).orElse(0L);}
    private void checkRevision(String workspace,Long id,long expected){if(revision(workspace,id)!=expected)throw new ResponseStatusException(HttpStatus.CONFLICT,"记录已在其他页面更新，请刷新后重试；本次内容尚未保存。");}
    private synchronized void save(String workspace,Long id,long expected,ObjectNode report) {
        checkRevision(workspace,id,expected);EvidenceDocument d=documents.findByWorkspaceAndScanRunId(workspace,id).orElseGet(EvidenceDocument::new);
        d.setWorkspace(workspace);d.setScanRunId(id);d.setRevision(expected+1);d.setDocument(report.toString());d.setUpdatedAt(LocalDateTime.now());documents.save(d);
    }
    public synchronized ObjectNode start(String workspace,Long id,long expected,int limit) {
        scan(id);checkRevision(workspace,id,expected);vault.active(workspace);
        if(limit<10||limit>80)throw new IllegalArgumentException("AI 分析范围应为 10–80 篇");
        if(EvidenceBuilder.eligible(corpus(id)).isEmpty())throw new IllegalArgumentException("请先获取可分析的文献");
        String key=workspace+":"+id;if("RUNNING".equals(job(workspace,id).path("status").asText()))throw new ResponseStatusException(HttpStatus.CONFLICT,"当前任务正在分析，请等待完成");
        if(jobs.values().stream().filter(j -> j.path("status").asText().equals("RUNNING")).count()>=2)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"已有两个分析任务，请稍后重试");
        jobs.put(key,mapper.createObjectNode().put("status","RUNNING").put("startedAt",java.time.Instant.now().toString()).put("timeoutSeconds",gateway.analysisTimeoutSeconds()).put("message","正在根据文献摘要梳理主题和引文，原有结果仍可阅读。"));
        executor.execute(()->{try{synthesize(workspace,id,expected,limit);finishJob(key,"COMPLETE","草稿已生成，请核对引文和主题边界。","");}
            catch(Exception e){String message=e instanceof IllegalArgumentException || e instanceof ModelConnectionException ? e.getMessage() : e instanceof ResponseStatusException ? ((ResponseStatusException)e).getReason() : "分析未完成，请重试；原有结果未被覆盖。";finishJob(key,"FAILED",message,e instanceof ModelConnectionException m?m.getCode():e instanceof EvidenceValidationException v?v.getCode():"analysis_failed");}});
        return job(workspace,id);
    }
    private void finishJob(String key,String status,String message,String code){jobs.computeIfPresent(key,(k,current)->current.deepCopy().put("status",status).put("message",message).put("code",code).put("endedAt",java.time.Instant.now().toString()));}
    void synthesize(String workspace,Long id,long expected,int limit) {
        ScanRun scan=scan(id);List<Article> all=corpus(id);checkRevision(workspace,id,expected);
        EvidenceSynthesis synthesis=new EvidenceSynthesis();EvidenceSynthesis.Submission input=synthesis.submission(all,limit);
        if(input.papers().isEmpty())throw new IllegalArgumentException("没有可用于分析的文献");
        ModelConnection model=vault.active(workspace);
        jobs.computeIfPresent(workspace+":"+id,(k,current)->current.deepCopy().put("sourceCount",input.papers().size()).put("inputChars",input.json().length()).put("provider",model.provider()).put("model",model.model()));
        ObjectNode generated=synthesis.generate(gateway,model,scan.getTopic(),input);
        ObjectNode report=baseline(all); report.set("topics",generated.path("topics"));report.set("candidates",generated.path("candidates"));report.put("mode","AI");
        report.put("aiAnalyzedCount",input.papers().size());report.put("model",model.model());report.put("citationMethod",generated.path("citationMethod").asText());report.put("sampling","优先纳入综述，其余按检索排序等距取样；每篇摘要最多2200字符，总标题摘要最多70000字符；原文按编号片段提供。");
        ArrayNode pmids=report.putArray("aiAnalyzedPmids");input.papers().forEach(a->pmids.add(a.getPmid()));
        if(!fingerprint(all).equals(fingerprint(corpus(id))))throw new ResponseStatusException(HttpStatus.CONFLICT,"分析期间文献发生变化，请重新分析；原有结果已保留。");
        save(workspace,id,expected,report);
    }
    public synchronized ObjectNode update(String workspace,Long id,ObjectNode request) {
        scan(id);if(!request.path("revision").isIntegralNumber() || !request.path("revision").canConvertToLong() || request.path("revision").asLong()<0)throw new IllegalArgumentException("请提供有效的记录版本");
        long expected=request.path("revision").asLong();checkRevision(workspace,id,expected);List<Article> all=corpus(id);
        ObjectNode report=documents.findByWorkspaceAndScanRunId(workspace,id).map(this::parse).orElseGet(()->baseline(all));
        Map<String,Article> source=EvidenceBuilder.eligible(all).stream().collect(Collectors.toMap(Article::getPmid,a->a,(a,b)->a));
        String operation=request.path("operation").asText();
        if(operation.equals("topic")) {
            ObjectNode topic=topic(report,request.path("topicId").asText());
            topic.put("name",EvidenceValidator.text(request,"name",160,true));topic.put("description",EvidenceValidator.text(request,"description",1500,false));
            topic.put("maturity",choice(request,"maturity",Set.of("UNASSESSED","MATURE","EMERGING","FRAGILE")));
            topic.put("editorStatus",choice(request,"editorStatus",Set.of("DRAFT","CONFIRMED")));topic.put("editorNote",EvidenceValidator.text(request,"editorNote",4000,false));
        } else if(operation.equals("note")) {
            ObjectNode topic=topic(report,request.path("topicId").asText());ArrayNode notes=(ArrayNode)topic.path("notes");String noteId=request.path("id").asText();
            ObjectNode note=mapper.createObjectNode();note.put("id",noteId.isBlank()?UUID.randomUUID().toString():noteId);
            note.put("kind",choice(request,"kind",Set.of("CONSENSUS","CONTROVERSY","GAP","CONTEXT")));note.put("text",EvidenceValidator.text(request,"text",4000,true));
            note.put("editorStatus",choice(request,"editorStatus",Set.of("DRAFT","CONFIRMED")));ArrayNode citations=new EvidenceValidator().citations(request.path("citations"),source,true);
            note.set("citations",citations);replace(notes,note); includeCitations(topic,citations);
        } else if(operation.equals("deleteNote")) {
            ArrayNode notes=(ArrayNode)topic(report,request.path("topicId").asText()).path("notes");String noteId=request.path("id").asText();
            for(int n=0;n<notes.size();n++)if(noteId.equals(notes.get(n).path("id").asText())){notes.remove(n);break;}
        } else if(operation.equals("coverage")) {
            ObjectNode topic=topic(report,request.path("topicId").asText());String pmid=EvidenceValidator.text(request,"reviewPmid",30,true);
            if(!source.containsKey(pmid)||!EvidenceBuilder.review(source.get(pmid)))throw new IllegalArgumentException("请选择本任务中的综述");
            String status=choice(request,"status",Set.of("UNVERIFIED","ABSTRACT_MENTION","DEEP","PARTIAL","NONE"));
            ObjectNode coverage=mapper.createObjectNode();coverage.put("reviewPmid",pmid);coverage.put("status",status);
            coverage.put("rationale",EvidenceValidator.text(request,"rationale",4000,!status.equals("UNVERIFIED")));
            if(Set.of("DEEP","PARTIAL","NONE").contains(status)) {
                if(!request.path("verifiedFullText").asBoolean())throw new IllegalArgumentException("请核对综述全文后再标记覆盖程度");
                coverage.put("fullTextNote",EvidenceValidator.text(request,"fullTextNote",8000,true));coverage.put("verifiedFullText",true);
            } else {
                ArrayNode refs=new EvidenceValidator().citations(request.path("citations"),source,status.equals("ABSTRACT_MENTION"));
                if(status.equals("ABSTRACT_MENTION") && !java.util.stream.StreamSupport.stream(refs.spliterator(),false).anyMatch(r -> pmid.equals(r.path("pmid").asText())))throw new IllegalArgumentException("请引用该综述的标题或摘要");
                coverage.set("citations",refs);includeCitations(topic,refs);
            }
            coverage.put("verificationBasis",Set.of("DEEP","PARTIAL","NONE").contains(status)?"EDITOR_FULLTEXT":"ABSTRACT");coverage.put("updatedAt",LocalDateTime.now().toString());
            ArrayNode rows=(ArrayNode)topic.path("coverage");boolean found=false;for(int i=0;i<rows.size();i++)if(pmid.equals(rows.get(i).path("reviewPmid").asText())){rows.set(i,coverage);found=true;break;}if(!found)rows.add(coverage);
        } else if(operation.equals("review")) {
            String pmid=EvidenceValidator.text(request,"pmid",30,true);
            if(!source.containsKey(pmid)||!EvidenceBuilder.review(source.get(pmid)))throw new IllegalArgumentException("请选择本任务中的综述");
            ObjectNode entry=mapper.createObjectNode();entry.put("pmid",pmid);
            entry.put("searchCutoff",EvidenceValidator.text(request,"searchCutoff",100,false));
            entry.put("scopeNote",EvidenceValidator.text(request,"scopeNote",6000,false));entry.put("limitations",EvidenceValidator.text(request,"limitations",6000,false));
            entry.put("editorStatus",choice(request,"editorStatus",Set.of("DRAFT","CONFIRMED")));entry.put("verificationBasis","EDITOR");entry.put("updatedAt",LocalDateTime.now().toString());
            ArrayNode inventory=report.has("reviewInventory")?(ArrayNode)report.path("reviewInventory"):report.putArray("reviewInventory");boolean replaced=false;
            for(int i=0;i<inventory.size();i++)if(pmid.equals(inventory.get(i).path("pmid").asText())){inventory.set(i,entry);replaced=true;break;}if(!replaced)inventory.add(entry);
        } else if(operation.equals("candidate")) {
            ObjectNode card=mapper.createObjectNode();String cardId=request.path("id").asText();card.put("id",cardId.isBlank()?UUID.randomUUID().toString():cardId);
            for(String field:List.of("title","question","whyNow","difference","boundary"))card.put(field,EvidenceValidator.text(request,field,3000,true));
            if(!request.path("outline").isArray() || request.path("outline").size()>10)throw new IllegalArgumentException("文章提纲应为最多10项的列表");
            ArrayNode outline=card.putArray("outline");for(JsonNode item:request.path("outline")){if(!item.isTextual()||item.asText().length()>600)throw new IllegalArgumentException("提纲内容过长");outline.add(item.asText());}
            card.put("status",choice(request,"status",Set.of("PENDING","KEEP","MERGE","NARROW","EVIDENCE_NEEDED","HOLD","REJECT")));
            card.put("editorNote",EvidenceValidator.text(request,"editorNote",4000,false));card.put("noveltyStatus","UNVERIFIED");
            ArrayNode refs=new EvidenceValidator().citations(request.path("citations"),source,true);card.set("citations",refs);
            ArrayNode pmids=card.putArray("pmids");Set<String> unique=new LinkedHashSet<>();refs.forEach(c->unique.add(c.path("pmid").asText()));unique.forEach(pmids::add);
            replace((ArrayNode)report.path("candidates"),card);
        } else throw new IllegalArgumentException("不支持的编辑操作");
        report.put("editedAt",LocalDateTime.now().toString());save(workspace,id,expected,report);return read(workspace,id);
    }
    private void includeCitations(ObjectNode topic,ArrayNode refs){Set<String> ids=new HashSet<>();topic.path("pmids").forEach(p->ids.add(p.asText()));refs.forEach(c->{String p=c.path("pmid").asText();if(ids.add(p))((ArrayNode)topic.path("pmids")).add(p);});}
    private ObjectNode topic(ObjectNode r,String id){for(JsonNode t:r.path("topics"))if(id.equals(t.path("id").asText()))return(ObjectNode)t;throw new IllegalArgumentException("主题不存在，请刷新");}
    private void replace(ArrayNode rows,ObjectNode value){for(int i=0;i<rows.size();i++)if(value.path("id").asText().equals(rows.get(i).path("id").asText())){rows.set(i,value);return;}rows.add(value);}
    private String choice(JsonNode r,String field,Set<String> allowed){String v=r.path(field).asText();if(!allowed.contains(v))throw new IllegalArgumentException("不支持的字段状态："+field);return v;}
    public Article article(Long id,String pmid){scan(id);return articles.findByScanRunIdAndPmid(id,pmid).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"本任务中没有该文献"));}
}
