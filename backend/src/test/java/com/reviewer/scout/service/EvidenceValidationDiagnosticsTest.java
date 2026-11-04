package com.reviewer.scout.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviewer.scout.entity.Article;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EvidenceValidationDiagnosticsTest {
    ObjectMapper mapper=new ObjectMapper();
    Article paper(){Article a=new Article();a.setPmid("123");a.setTitle("Endpoint methods");a.setAbstractText("The endpoint was assessed at week 24.");return a;}
    String report(String quote){return "{\"topics\":[{\"name\":\"终点\",\"pmids\":[\"123\"],\"notes\":[{\"kind\":\"CONTEXT\",\"text\":\"终点评估\",\"citations\":[{\"pmid\":\"123\",\"field\":\"abstract\",\"quote\":\""+quote+"\"}]}],\"coverage\":[]}],\"candidates\":[]}";}
    @Test void failuresIdentifyTheExactRuleAndLocationWithoutEchoingGeneratedText() throws Exception {
        var error=assertThrows(EvidenceValidationException.class,()->new EvidenceValidator().validate(mapper.readTree(report("The endpoint was assessed at week 52.")),List.of(paper())));
        assertEquals("quote_mismatch",error.getCode());assertEquals("$.topics[0].notes[0].citations[0].quote",error.getPath());
        assertTrue(error.getMessage().contains("123"));assertFalse(error.getMessage().contains("week 52"));
    }
    @Test void missingOptionalArraysAreDistinguishedFromFabricatedReferences() throws Exception {
        var input=mapper.readTree("{\"topics\":[{\"name\":\"终点\",\"pmids\":[\"123\"],\"notes\":[]}],\"candidates\":[]}");
        var error=assertThrows(EvidenceValidationException.class,()->new EvidenceValidator().validate(input,List.of(paper())));
        assertEquals("invalid_array",error.getCode());assertEquals("$.topics[0].coverage",error.getPath());
    }
}
