package com.reviewer.scout.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "SCOUT_LIVE_PUBMED", matches = "true")
class PubMedLiveTest {
    @Test void officialApiProvidesDiagnosticsAndAnOriginalAbstract() throws Exception {
        PubMedService service = new PubMedService();
        ReflectionTestUtils.setField(service, "baseUrl", "https://eutils.ncbi.nlm.nih.gov/entrez/eutils");
        ReflectionTestUtils.setField(service, "tool", "reviewer-scout"); ReflectionTestUtils.setField(service, "email", ""); ReflectionTestUtils.setField(service, "minInterval", 400L);
        assertEquals(1, service.preview("33301246[pmid]", "relevance").get("total"));
        var result = service.fetchArticles(List.of("33301246")); assertEquals(1, result.size()); assertFalse(result.get(0).getAbstractText().isBlank()); assertFalse(result.get(0).getTitle().isBlank()); assertNotNull(result.get(0).getDoi());
    }
}
