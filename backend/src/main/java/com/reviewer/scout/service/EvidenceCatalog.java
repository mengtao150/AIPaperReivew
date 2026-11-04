package com.reviewer.scout.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.reviewer.scout.entity.Article;
import java.util.*;

/** The server owns quotation text. Models select immutable IDs from the submitted catalog. */
public class EvidenceCatalog {
    private final ObjectMapper mapper=new ObjectMapper();
    private final Map<String,ObjectNode> evidence=new LinkedHashMap<>();
    private final Map<String,ArrayNode> byPaper=new LinkedHashMap<>();
    public EvidenceCatalog(List<Article> submitted) {
        int paperIndex=0;
        for(Article article:submitted) {
            String prefix="P"+(++paperIndex);byPaper.put(article.getPmid(),mapper.createArrayNode());
            add(article.getPmid(),prefix+"-T","title",Objects.toString(article.getTitle(),""));
            String text=Objects.toString(article.getAbstractText(),"");int start=0,part=0;
            while(start<text.length()) {
                int end=Math.min(start+350,text.length());
                if(end<text.length()) {
                    int sentence=text.lastIndexOf(". ",end-1);
                    if(sentence>start+170)end=sentence+1;
                    else {int space=text.lastIndexOf(' ',end-1);if(space>start+200)end=space;}
                    if(end>start && Character.isHighSurrogate(text.charAt(end-1)))end--;
                }
                add(article.getPmid(),prefix+"-A"+(++part),"abstract",text.substring(start,end));start=end;
            }
        }
    }
    private void add(String pmid,String id,String field,String text) {
        String quote=text.strip();if(quote.length()<8 || quote.length()>2500)return;
        ObjectNode row=mapper.createObjectNode();row.put("evidenceId",id);row.put("pmid",pmid);row.put("field",field);row.put("quote",quote);
        evidence.put(id,row);byPaper.get(pmid).add(row);
    }
    public ArrayNode fragments(String pmid){return byPaper.getOrDefault(pmid,mapper.createArrayNode()).deepCopy();}
    public ObjectNode bind(JsonNode raw) {
        if(raw==null || !raw.isObject())throw new EvidenceValidationException("invalid_document","$","应返回一个 JSON 对象");
        ObjectNode result=raw.deepCopy();optionalArray(result,"candidates");
        JsonNode topics=result.path("topics");
        if(topics.isArray())for(int n=0;n<topics.size();n++) {
            if(!(topics.get(n) instanceof ObjectNode topic))continue;String path="$.topics["+n+"]";
            optionalArray(topic,"notes");optionalArray(topic,"coverage");optionalText(topic,"description");normalizeIds(topic);
            JsonNode notes=topic.path("notes");if(notes.isArray())for(int i=0;i<notes.size();i++)if(notes.get(i) instanceof ObjectNode note)bindCitations(note,path+".notes["+i+"]",topic);
            JsonNode coverage=topic.path("coverage");if(coverage.isArray())for(int i=0;i<coverage.size();i++)if(coverage.get(i) instanceof ObjectNode row) {
                if(row.path("reviewPmid").isIntegralNumber())row.put("reviewPmid",row.path("reviewPmid").asText());
                optionalArray(row,"citations");optionalText(row,"rationale");bindCitations(row,path+".coverage["+i+"]",topic);
            }
        }
        JsonNode candidates=result.path("candidates");if(candidates.isArray())for(int n=0;n<candidates.size();n++)if(candidates.get(n) instanceof ObjectNode card){normalizeIds(card);bindCitations(card,"$.candidates["+n+"]",card);}
        return result;
    }
    private void bindCitations(ObjectNode owner,String path,ObjectNode associated) {
        JsonNode citations=owner.path("citations");if(!citations.isArray())return;
        ArrayNode bound=mapper.createArrayNode();
        for(int n=0;n<citations.size();n++) {
            JsonNode citation=citations.get(n);String p=path+".citations["+n+"]";
            if(citation.has("evidenceId")) {
                String id=citation.path("evidenceId").isTextual()?citation.path("evidenceId").asText().strip():"";
                ObjectNode original=evidence.get(id);
                if(original==null)throw new EvidenceValidationException("unknown_evidence",p+".evidenceId","引用的片段编号不在本轮提供的原文中");
                for(String field:List.of("pmid","field","quote"))if(citation.has(field) && !citation.path(field).asText().equals(original.path(field).asText()))throw new EvidenceValidationException("conflicting_evidence",p+"."+field,"片段编号与附加的来源信息冲突");
                bound.add(original.deepCopy());
            } else {
                // Compatibility with existing drafts; free-text quotes still receive strict validation.
                JsonNode legacy=citation.deepCopy();if(legacy instanceof ObjectNode object && object.path("pmid").isIntegralNumber())object.put("pmid",object.path("pmid").asText());bound.add(legacy);
            }
        }
        owner.set("citations",bound);
        JsonNode ids=associated.path("pmids");if(ids.isArray()) {
            Set<String> seen=new HashSet<>();ids.forEach(p->seen.add(p.asText()));
            for(JsonNode ref:bound) {String pmid=ref.path("pmid").asText();if(byPaper.containsKey(pmid) && seen.add(pmid))((ArrayNode)ids).add(pmid);}
        }
    }
    private void normalizeIds(ObjectNode owner) {
        optionalArray(owner,"pmids");JsonNode ids=owner.path("pmids");if(!ids.isArray())return;
        ArrayNode normalized=mapper.createArrayNode();for(JsonNode id:ids){if(id.isIntegralNumber())normalized.add(id.asText());else if(id.isTextual())normalized.add(id.asText().strip());else normalized.add(id);}owner.set("pmids",normalized);
    }
    private void optionalArray(ObjectNode owner,String field){if(!owner.hasNonNull(field))owner.putArray(field);}
    private void optionalText(ObjectNode owner,String field){if(owner.has(field) && owner.path(field).isNull())owner.remove(field);}
}
