package com.reviewer.scout.service;

import com.reviewer.scout.entity.*;
import com.reviewer.scout.repository.ArticleRepository;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArticleContactServiceTest {
    Article article(long id, long scan, String pmid) { Article a = new Article(); a.setId(id); a.setScanRunId(scan); a.setPmid(pmid); a.setTitle("Original title"); return a; }
    @Test void refreshPreservesThePaperAndOnlyStoresSourceBackedContacts() throws Exception {
        ArticleRepository repository = mock(ArticleRepository.class); PubMedService pubmed = mock(PubMedService.class);
        Article stored = article(1, 10, "123"), fresh = article(9, 99, "123"); fresh.setTitle("Remote title"); fresh.setEmailContacts(List.of(new PublicationEmail("alice@example.edu", List.of("Alice Chen"), "https://pubmed.ncbi.nlm.nih.gov/123/", "PubMed 作者单位", "alice@example.edu")));
        when(repository.findAllById(List.of(1L))).thenReturn(List.of(stored)); when(pubmed.fetchArticles(List.of("123"))).thenReturn(List.of(fresh));
        var result = assertDoesNotThrow(() -> new ArticleContactService(repository, pubmed).refresh(10L, List.of(1L)));
        assertEquals(1, result.get("checkedCount")); assertEquals(1, result.get("foundCount")); assertEquals("Original title", stored.getTitle()); assertEquals("alice@example.edu", stored.getEmailContacts().get(0).email()); assertNotNull(stored.getEmailCheckedAt());
    }
    @Test void missingRemoteRecordIsNotMarkedAsCheckedAndWrongScanIsRejected() throws Exception {
        ArticleRepository repository = mock(ArticleRepository.class); PubMedService pubmed = mock(PubMedService.class); Article stored = article(1, 10, "123");
        when(repository.findAllById(List.of(1L))).thenReturn(List.of(stored)); when(pubmed.fetchArticles(List.of("123"))).thenReturn(List.of());
        var result = assertDoesNotThrow(() -> new ArticleContactService(repository, pubmed).refresh(10L, List.of(1L))); assertEquals(0, result.get("checkedCount")); assertEquals(1, result.get("unavailableCount")); assertNull(stored.getEmailCheckedAt());
        assertThrows(IllegalArgumentException.class, () -> new ArticleContactService(repository, pubmed).refresh(20L, List.of(1L)));
        assertThrows(IllegalArgumentException.class, () -> new ArticleContactService(repository, pubmed).refresh(10L, Collections.nCopies(101, 1L)));
    }
    @Test void converterRetainsProvenanceAcrossDatabaseRoundTripAndHandlesLegacyNull() {
        PublicationEmailConverter converter = new PublicationEmailConverter();
        List<PublicationEmail> original = List.of(new PublicationEmail("alice@example.edu", List.of("Alice Chen"), "https://pubmed.ncbi.nlm.nih.gov/123/", "PubMed 作者单位", "alice@example.edu"));
        assertEquals(original, converter.convertToEntityAttribute(converter.convertToDatabaseColumn(original))); assertEquals(List.of(), converter.convertToEntityAttribute(null));
    }
}
