package com.reviewer.scout.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.reviewer.scout.entity.Article;
import java.util.*;
import java.util.stream.Collectors;

/** Validate against exact submitted sources, with safe field diagnostics. */
public class EvidenceValidator {
    private final ObjectMapper mapper=new ObjectMapper();
    public ObjectNode validate(JsonNode input,List<Article> submitted) {
        if(input==null || !input.isObject())throw failure("invalid_document","$","应返回一个 JSON 对象");
        array(input.path("topics"),"$.topics",12,true);array(input.path("candidates"),"$.candidates",12,false);
        Map<String,Article> papers=submitted.stream().collect(Collectors.toMap(Article::getPmid,a->a,(a,b)->a));
        ObjectNode clean=mapper.createObjectNode();ArrayNode topics=clean.putArray("topics");
        for(int index=0;index<input.path("topics").size();index++) {
            JsonNode raw=input.path("topics").get(index);String path="$.topics["+index+"]";
            ObjectNode topic=topics.addObject();topic.put("id","t"+topics.size());topic.put("name",read(raw,"name",160,true,path));
            topic.put("description",read(raw,"description",1500,false,path));topic.put("maturity","UNASSESSED");topic.put("editorStatus","DRAFT");topic.put("editorNote","");
            topic.set("pmids",ids(raw.path("pmids"),papers,true,path+".pmids"));topic.putArray("terms");topic.putArray("assignments");
            array(raw.path("notes"),path+".notes",15,false);ArrayNode notes=topic.putArray("notes");
            for(int n=0;n<raw.path("notes").size();n++) {
                JsonNode source=raw.path("notes").get(n);String p=path+".notes["+n+"]";
                String kind=read(source,"kind",30,true,p);
                if(!Set.of("CONSENSUS","CONTROVERSY","GAP","CONTEXT").contains(kind))throw failure("invalid_note_kind",p+".kind","笔记类型不受支持");
                ObjectNode note=notes.addObject();note.put("id",UUID.randomUUID().toString());note.put("kind",kind);
                note.put("text",read(source,"text",4000,true,p));note.put("editorStatus","DRAFT");
                note.set("citations",citations(source.path("citations"),papers,true,p+".citations"));
                requireAssociated(topic.path("pmids"),note.path("citations"),p+".citations");
            }
            array(raw.path("coverage"),path+".coverage",80,false);ArrayNode coverage=topic.putArray("coverage");Set<String> seen=new HashSet<>();
            for(int n=0;n<raw.path("coverage").size();n++) {
                JsonNode source=raw.path("coverage").get(n);String p=path+".coverage["+n+"]";
                String pmid=read(source,"reviewPmid",30,true,p),status=read(source,"status",25,true,p);
                if(!papers.containsKey(pmid))throw failure("unknown_pmid",p+".reviewPmid","综述编号不在本轮提供的文献中");
                if(!EvidenceBuilder.review(papers.get(pmid)))throw failure("not_a_review",p+".reviewPmid","该文献未被识别为综述");
                if(!seen.add(pmid))throw failure("duplicate_review",p+".reviewPmid","同一方向包含重复综述条目");
                if(!Set.of("UNVERIFIED","ABSTRACT_MENTION").contains(status))throw failure("invalid_coverage_status",p+".status","摘要分析只能标记尚未核实或摘要涉及");
                ObjectNode row=coverage.addObject();row.put("reviewPmid",pmid);row.put("status",status);row.put("rationale",read(source,"rationale",2000,false,p));
                ArrayNode refs=citations(source.path("citations"),papers,!status.equals("UNVERIFIED"),p+".citations");
                if(status.equals("ABSTRACT_MENTION") && !java.util.stream.StreamSupport.stream(refs.spliterator(),false).anyMatch(r->pmid.equals(r.path("pmid").asText())))throw failure("review_citation_missing",p+".citations","覆盖判断没有引用该综述本身");
                row.set("citations",refs);requireAssociated(topic.path("pmids"),refs,p+".citations");
            }
        }
        ArrayNode cards=clean.putArray("candidates");
        for(int index=0;index<input.path("candidates").size();index++) {
            JsonNode source=input.path("candidates").get(index);String path="$.candidates["+index+"]";ObjectNode card=cards.addObject();card.put("id",UUID.randomUUID().toString());
            for(String field:List.of("title","question","whyNow","difference","boundary"))card.put(field,read(source,field,3000,true,path));
            array(source.path("outline"),path+".outline",10,false);ArrayNode outline=card.putArray("outline");
            for(int n=0;n<source.path("outline").size();n++) {JsonNode item=source.path("outline").get(n);if(!item.isTextual() || item.asText().length()>600)throw failure("invalid_field",path+".outline["+n+"]","提纲内容应为不超过600字符的文本");outline.add(item.asText());}
            card.set("pmids",ids(source.path("pmids"),papers,true,path+".pmids"));card.set("citations",citations(source.path("citations"),papers,true,path+".citations"));
            requireAssociated(card.path("pmids"),card.path("citations"),path+".citations");
            card.put("status","PENDING");card.put("editorNote","");card.put("noveltyStatus","UNVERIFIED");
        }
        return clean;
    }
    private void requireAssociated(JsonNode pmids,JsonNode citations,String path) {
        Set<String> associated=new HashSet<>();pmids.forEach(p->associated.add(p.asText()));
        for(int n=0;n<citations.size();n++)if(!associated.contains(citations.get(n).path("pmid").asText()))throw failure("unassociated_pmid",path+"["+n+"].pmid","引用文献没有列入本方向或选题的关联文献列表");
    }
    public ArrayNode citations(JsonNode raw,Map<String,Article> papers,boolean required){return citations(raw,papers,required,"$.citations");}
    private ArrayNode citations(JsonNode raw,Map<String,Article> papers,boolean required,String path) {
        array(raw,path,20,required);ArrayNode refs=mapper.createArrayNode();
        for(int n=0;n<raw.size();n++) {
            JsonNode source=raw.get(n);String p=path+"["+n+"]";
            String pmid=read(source,"pmid",30,true,p),field=read(source,"field",20,true,p),quote=read(source,"quote",2500,true,p);
            Article article=papers.get(pmid);if(article==null)throw failure("unknown_pmid",p+".pmid","引用了本轮未提供的文献");
            if(!Set.of("title","abstract").contains(field))throw failure("invalid_source_field",p+".field","引文来源应为标题或摘要");
            String original=field.equals("title")?article.getTitle():article.getAbstractText();
            if(quote.length()<8)throw failure("quote_too_short",p+".quote","原文片段少于8个字符");
            if(original==null || !normalize(original).contains(normalize(quote)))throw failure("quote_mismatch",p+".quote","引文与 PMID "+pmid+" 的"+(field.equals("title")?"标题":"摘要")+"不一致");
            refs.addObject().put("pmid",pmid).put("field",field).put("quote",quote);
        }
        return refs;
    }
    private ArrayNode ids(JsonNode raw,Map<String,Article> papers,boolean required,String path) {
        array(raw,path,10000,required);ArrayNode ids=mapper.createArrayNode();Set<String> seen=new HashSet<>();
        for(int n=0;n<raw.size();n++) {
            JsonNode item=raw.get(n);if(!item.isTextual())throw failure("invalid_pmid_type",path+"["+n+"]","文献编号应为字符串");
            if(!papers.containsKey(item.asText()))throw failure("unknown_pmid",path+"["+n+"]","关联文献不在本轮提供的文献中");
            if(seen.add(item.asText()))ids.add(item.asText());
        }
        return ids;
    }
    private static void array(JsonNode raw,String path,int max,boolean required) {
        if(!raw.isArray())throw failure("invalid_array",path,"该字段应为数组，无内容时应返回空数组");
        if(raw.size()>max || required && raw.isEmpty())throw failure("invalid_array_length",path,"数组长度不符合要求");
    }
    public static String text(JsonNode source,String field,int limit,boolean required){return read(source,field,limit,required,"$");}
    private static String read(JsonNode source,String field,int limit,boolean required,String path) {
        JsonNode value=source.path(field);
        if(!value.isMissingNode() && !value.isTextual())throw failure("invalid_field",path+"."+field,"该字段应为文本");
        if(value.asText().length()>limit || required && value.asText().isBlank())throw failure("invalid_field",path+"."+field,"必填内容为空或内容过长");
        return value.asText("").strip();
    }
    private static String normalize(String text){return text.replaceAll("\\s+"," ").strip();}
    private static EvidenceValidationException failure(String code,String path,String reason){return new EvidenceValidationException(code,path,reason);}
}
