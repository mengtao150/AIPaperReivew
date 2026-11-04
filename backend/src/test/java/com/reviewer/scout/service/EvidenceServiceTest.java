package com.reviewer.scout.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.reviewer.scout.entity.*;
import com.reviewer.scout.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EvidenceServiceTest {
    ArticleRepository articles=mock(ArticleRepository.class); ScanRunRepository scans=mock(ScanRunRepository.class);
    EvidenceDocumentRepository documents=mock(EvidenceDocumentRepository.class); ChatGateway gateway=mock(ChatGateway.class); ModelVault vault=mock(ModelVault.class);
    Map<String,EvidenceDocument> db=new HashMap<>(); ObjectMapper mapper=new ObjectMapper(); EvidenceService service;
    @BeforeEach void setup() {
        ScanRun scan=new ScanRun(); scan.setId(1L); scan.setTopic("Endpoint methods"); scan.setQuery("endpoints"); scan.setTotalHits(1);
        Article a=new Article(); a.setId(1L); a.setPmid("11"); a.setTitle("Endpoint assessment"); a.setAbstractText("Allocation was concealed."); a.setMeshTerms("Endpoints");
        when(scans.findById(1L)).thenReturn(Optional.of(scan)); when(articles.findByScanRunId(eq(1L),any())).thenReturn(new PageImpl<>(List.of(a)));
        when(documents.findByWorkspaceAndScanRunId(anyString(),eq(1L))).thenAnswer(i -> Optional.ofNullable(db.get(i.getArgument(0))));
        when(documents.save(any())).thenAnswer(i -> {EvidenceDocument d=i.getArgument(0); db.put(d.getWorkspace(),d); return d;});
        when(vault.active("owner")).thenReturn(new ModelConnection("m","model","deepseek","https://api.deepseek.com","test","fake"));
        when(gateway.analysisTimeoutSeconds()).thenReturn(300);
        service=new EvidenceService(articles,scans,documents,vault,gateway);
    }
    @AfterEach void stop() {service.close();}
    @Test void editsSurviveReloadAndRemainIsolatedWithConflictProtection() throws Exception {
        assertEquals(0,service.read("owner",1L).path("revision").asLong());
        ObjectNode change=(ObjectNode)mapper.readTree("{\"revision\":0,\"operation\":\"topic\",\"topicId\":\"t1\",\"name\":\"终点方法\",\"description\":\"明确范围\",\"maturity\":\"UNASSESSED\",\"editorStatus\":\"CONFIRMED\",\"editorNote\":\"保留\"}");
        service.update("owner",1L,change);
        assertEquals("终点方法",service.read("owner",1L).path("report").path("topics").get(0).path("name").asText());
        assertEquals("Endpoints",service.read("other",1L).path("report").path("topics").get(0).path("name").asText());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.update("owner",1L,change)).getStatusCode().value());
    }
    @Test void failedSynthesisDoesNotReplaceEditorialWork() throws Exception {
        ObjectNode change=(ObjectNode)mapper.readTree("{\"revision\":0,\"operation\":\"topic\",\"topicId\":\"t1\",\"name\":\"Saved topic\",\"maturity\":\"UNASSESSED\",\"editorStatus\":\"DRAFT\"}");
        service.update("owner",1L,change); when(gateway.completeAnalysis(any(),anyList(),anyInt())).thenReturn("{\"topics\":[],\"candidates\":[]}");
        assertThrows(IllegalArgumentException.class,()->service.synthesize("owner",1L,1,40));
        assertEquals("Saved topic",service.read("owner",1L).path("report").path("topics").get(0).path("name").asText());
        assertEquals(1,service.read("owner",1L).path("revision").asLong());
    }
    @Test void validSynthesisSavesOnlySourceBoundedDraftsAndDisclosesModelScope() {
        when(gateway.completeAnalysis(any(),anyList(),anyInt())).thenReturn("{\"topics\":[{\"name\":\"终点方法\",\"description\":\"方法学问题\",\"pmids\":[\"11\"],\"notes\":[{\"kind\":\"CONTEXT\",\"text\":\"该文报告分配隐藏。\",\"citations\":[{\"pmid\":\"11\",\"field\":\"abstract\",\"quote\":\"Allocation was concealed.\"}]}],\"coverage\":[]}],\"candidates\":[]}");
        service.synthesize("owner",1L,0,40);
        var report=service.read("owner",1L).path("report");
        assertEquals("AI",report.path("mode").asText()); assertEquals(1,report.path("aiAnalyzedCount").asInt());
        assertEquals("DRAFT",report.path("topics").get(0).path("notes").get(0).path("editorStatus").asText());
        verify(gateway).completeAnalysis(any(),anyList(),eq(8000));
    }
    @Test void invalidEditorialQuoteCannotSaveANoteOrAdvanceTheRevision() throws Exception {
        ObjectNode change=(ObjectNode)mapper.readTree("{\"revision\":0,\"operation\":\"note\",\"topicId\":\"t1\",\"kind\":\"CONSENSUS\",\"text\":\"不支持的判断\",\"editorStatus\":\"CONFIRMED\",\"citations\":[{\"pmid\":\"11\",\"field\":\"abstract\",\"quote\":\"This sentence was not in the paper.\"}]}");
        assertThrows(IllegalArgumentException.class,()->service.update("owner",1L,change));
        assertEquals(0,service.read("owner",1L).path("revision").asLong());verify(documents,never()).save(any());
    }
    @Test void reviewInventoryIsSavedSeparatelyFromPublicationDateAndCoverage() throws Exception {
        Article review=articles.findByScanRunId(1L,PageRequest.of(0,1000)).getContent().get(0);review.setPubtypes("Systematic Review");review.setPubdate("2026-10");
        ObjectNode change=(ObjectNode)mapper.readTree("{\"revision\":0,\"operation\":\"review\",\"pmid\":\"11\",\"searchCutoff\":\"未报告\",\"scopeNote\":\"核对范围\",\"limitations\":\"待核实局限\",\"editorStatus\":\"DRAFT\"}");
        var saved=service.update("owner",1L,change).path("report");
        assertEquals("未报告",saved.path("reviewInventory").get(0).path("searchCutoff").asText());
        assertEquals("2026-10",saved.path("sources").get(0).path("pubdate").asText());
        assertEquals("UNVERIFIED",saved.path("topics").get(0).path("coverage").get(0).path("status").asText());
    }
    @Test void fullTextCoverageRequiresAnExplicitHumanRecord() throws Exception {
        Article review=articles.findByScanRunId(1L,PageRequest.of(0,1000)).getContent().get(0);review.setPubtypes("Review");
        ObjectNode change=(ObjectNode)mapper.readTree("{\"revision\":0,\"operation\":\"coverage\",\"topicId\":\"t1\",\"reviewPmid\":\"11\",\"status\":\"DEEP\",\"rationale\":\"正文深入讨论\"}");
        assertThrows(IllegalArgumentException.class,()->service.update("owner",1L,change));change.put("verifiedFullText",true);change.put("fullTextNote","编辑核对第3节，记录其讨论范围和局限。");
        var row=service.update("owner",1L,change).path("report").path("topics").get(0).path("coverage").get(0);
        assertEquals("EDITOR_FULLTEXT",row.path("verificationBasis").asText());assertTrue(row.path("verifiedFullText").asBoolean());
    }
    @Test void timedOutBackgroundAnalysisRetainsWorkAndExposesItsActualBudget() throws Exception {
        ObjectNode change=(ObjectNode)mapper.readTree("{\"revision\":0,\"operation\":\"topic\",\"topicId\":\"t1\",\"name\":\"Saved topic\",\"maturity\":\"UNASSESSED\",\"editorStatus\":\"DRAFT\"}");
        service.update("owner",1L,change);
        when(gateway.completeAnalysis(any(),anyList(),anyInt())).thenThrow(new ModelConnectionException("timeout","等待超过 300 秒，原有结果未被覆盖",null));
        service.start("owner",1L,1,20);
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(3),()->{while(!service.job("owner",1L).path("status").asText().equals("FAILED"))Thread.sleep(5);});
        var job=service.job("owner",1L);assertEquals(300,job.path("timeoutSeconds").asInt());assertEquals("timeout",job.path("code").asText());
        assertFalse(job.path("startedAt").asText().isBlank());assertFalse(job.path("endedAt").asText().isBlank());assertEquals(1,job.path("sourceCount").asInt());
        var saved=service.read("owner",1L);assertEquals(1,saved.path("revision").asLong());assertEquals("Saved topic",saved.path("report").path("topics").get(0).path("name").asText());
    }
    @Test void catalogBasedModelResponsePersistsServerBoundQuotes() {
        when(gateway.completeAnalysis(any(),anyList(),anyInt())).thenReturn("{\"topics\":[{\"name\":\"终点方法\",\"pmids\":[11],\"notes\":[{\"kind\":\"CONTEXT\",\"text\":\"该文报告分配隐藏。\",\"citations\":[{\"evidenceId\":\"P1-A1\"}]}]}]}");
        service.synthesize("owner",1L,0,20);
        var report=service.read("owner",1L).path("report");assertEquals("SOURCE_IDS",report.path("citationMethod").asText());
        var ref=report.path("topics").get(0).path("notes").get(0).path("citations").get(0);
        assertEquals("11",ref.path("pmid").asText());assertEquals("Allocation was concealed.",ref.path("quote").asText());
    }
}
