package com.reviewer.scout.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.reviewer.scout.entity.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EvidenceBuilderTest {
    final ObjectMapper mapper = new ObjectMapper();
    Article paper(String pmid, String title, String mesh, String types, String date) {
        Article a = new Article(); a.setId(Long.valueOf(pmid)); a.setPmid(pmid); a.setTitle(title);
        a.setAbstractText("Allocation was concealed. The endpoint was assessed at week 24.");
        a.setMeshTerms(mesh); a.setPubtypes(types); a.setPubdate(date); a.setAuthors("Alice Chen; Bob Li"); return a;
    }
    @Test void baselineSupportsOverlappingThemesAndDoesNotInventConsensusOrReviewCoverage() {
        var a = paper("1", "Endpoint selection", "Endpoints|Sample Size", "Journal Article", "2025-01");
        var b = paper("2", "Endpoint review", "Endpoints|Sample Size", "Systematic Review", "2026-02");
        ObjectNode report = new EvidenceBuilder().build(List.of(a, b));
        assertTrue(report.path("topics").size() >= 2);
        assertEquals(1, report.path("reviews").size());
        report.path("topics").forEach(t -> {
            assertEquals(2, t.path("pmids").size()); assertEquals(0, t.path("notes").size());
            t.path("coverage").forEach(c -> assertEquals("UNVERIFIED", c.path("status").asText()));
        });
        assertEquals("2025-01", report.path("timeline").get(0).path("month").asText());
        assertEquals("2026-02", report.path("timeline").get(1).path("month").asText());
    }
    @Test void retractionsAndMissingDatesAreDisclosedWithoutFalseMonthlyData() {
        var a = paper("1", "Undated paper", "", "Journal Article", "2025");
        var b = paper("2", "Withdrawn paper", "Endpoints", "Retracted Publication", "2026-02"); b.setIsRetracted(true);
        ObjectNode report = new EvidenceBuilder().build(List.of(a,b));
        assertEquals(1, report.path("analyzedCount").asInt()); assertEquals(1, report.path("excludedCount").asInt());
        assertEquals(1, report.path("undatedCount").asInt()); assertEquals(0, report.path("timeline").size());
        assertEquals(0, report.path("reviews").size());
    }
    @Test void modelCannotCiteUnknownPapersOrInventQuotes() throws Exception {
        var a = paper("1", "Endpoint selection", "Endpoints", "Journal Article", "2026-01");
        String valid = "{\"topics\":[{\"name\":\"终点选择\",\"pmids\":[\"1\"],\"notes\":[{\"kind\":\"GAP\",\"text\":\"需核查终点评估方法。\",\"citations\":[{\"pmid\":\"1\",\"field\":\"abstract\",\"quote\":\"The endpoint was assessed at week 24.\"}]}],\"coverage\":[]}],\"candidates\":[]}";
        assertDoesNotThrow(() -> new EvidenceValidator().validate(mapper.readTree(valid), List.of(a)));
        assertThrows(IllegalArgumentException.class, () -> new EvidenceValidator().validate(mapper.readTree(valid.replace("week 24", "week 52")), List.of(a)));
        assertThrows(IllegalArgumentException.class, () -> new EvidenceValidator().validate(mapper.readTree(valid.replace("\"1\"", "\"999\"")), List.of(a)));
        assertThrows(IllegalArgumentException.class, () -> new EvidenceValidator().validate(mapper.readTree(valid.replace("\"citations\":[{\"pmid\":\"1\",\"field\":\"abstract\",\"quote\":\"The endpoint was assessed at week 24.\"}]", "\"citations\":[]")), List.of(a)));
    }
    @Test void modelReferencesMustBelongToTheirTopicAndCandidate() throws Exception {
        var a=paper("1","Endpoint selection","Endpoints","Journal Article","2026-01");
        var b=paper("2","Different study","Sample Size","Journal Article","2026-02");
        String input="{\"topics\":[{\"name\":\"终点\",\"pmids\":[\"2\"],\"notes\":[{\"kind\":\"CONTEXT\",\"text\":\"分配隐藏\",\"citations\":[{\"pmid\":\"1\",\"field\":\"abstract\",\"quote\":\"Allocation was concealed.\"}]}],\"coverage\":[]}],\"candidates\":[]}";
        assertThrows(IllegalArgumentException.class,()->new EvidenceValidator().validate(mapper.readTree(input),List.of(a,b)));
    }
    @Test void synthesisSubmissionBoundsAbstractsAndMakesTruncationExplicit() {
        Article a=paper("1","Long methods study","Endpoints","Journal Article","2026-01");a.setAbstractText("A".repeat(2400)+"This sentence is outside the submitted excerpt.");
        var input=new EvidenceSynthesis().submission(List.of(a),20);
        assertEquals(2200,input.papers().get(0).getAbstractText().length());assertTrue(input.json().contains("\"abstractTruncated\":true"));
        assertFalse(input.json().contains("outside the submitted excerpt"));
    }
}
