package com.reviewer.scout.service;

import com.fasterxml.jackson.databind.*;
import com.reviewer.scout.entity.Article;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EvidenceCatalogTest {
    ObjectMapper mapper=new ObjectMapper();
    Article paper(){Article a=new Article();a.setPmid("123");a.setTitle("Endpoint methods");a.setAbstractText("The endpoint was assessed at week 24. Allocation was concealed.");return a;}
    String report(String id){return "{\"topics\":[{\"name\":\"终点\",\"pmids\":[123],\"notes\":[{\"kind\":\"CONTEXT\",\"text\":\"终点评估\",\"citations\":[{\"evidenceId\":\""+id+"\"}]}]}],\"candidates\":[]}";}
    @Test void idsBindToOriginalQuotesWithoutRequiringTheModelToCopyThem() throws Exception {
        EvidenceCatalog catalog=new EvidenceCatalog(List.of(paper()));
        var bound=catalog.bind(mapper.readTree(report("P1-A1")));
        var validated=new EvidenceValidator().validate(bound,List.of(paper()));
        var citation=validated.path("topics").get(0).path("notes").get(0).path("citations").get(0);
        assertEquals("123",citation.path("pmid").asText());assertEquals("abstract",citation.path("field").asText());
        assertEquals(paper().getAbstractText(),citation.path("quote").asText());
    }
    @Test void unknownSourceIdsAndContradictorySourceMetadataAreRejected() throws Exception {
        EvidenceCatalog catalog=new EvidenceCatalog(List.of(paper()));
        var error=assertThrows(EvidenceValidationException.class,()->catalog.bind(mapper.readTree(report("P9-A1"))));assertEquals("unknown_evidence",error.getCode());
        var conflict=mapper.readTree(report("P1-A1").replace("\"evidenceId\":\"P1-A1\"","\"evidenceId\":\"P1-A1\",\"pmid\":\"999\""));
        assertThrows(EvidenceValidationException.class,()->catalog.bind(conflict));
    }
    @Test void allCatalogQuotesAreBoundedContiguousSlicesOfTheSubmittedText() {
        Article a=paper();a.setAbstractText(("A long original evidence sentence with a measured outcome. ").repeat(60));
        EvidenceCatalog catalog=new EvidenceCatalog(List.of(a));var rows=catalog.fragments("123");assertTrue(rows.size()>5);
        for(JsonNode row:rows) {String text=row.path("quote").asText();assertTrue(text.length()<=350);assertTrue((row.path("field").asText().equals("title")?a.getTitle():a.getAbstractText()).contains(text));}
    }
}
