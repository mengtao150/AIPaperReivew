package com.reviewer.scout.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.reviewer.scout.entity.Article;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class PubMedParsingTest {
    @Test void structuredAbstractAndDoiOutsideCitationArePreserved() {
        String xml = "<PubmedArticleSet><PubmedArticle><MedlineCitation><PMID>123</PMID><Article><ArticleTitle>Trial of A</ArticleTitle><Abstract><AbstractText Label='METHODS'>100 participants</AbstractText><AbstractText Label='RESULTS'>No difference</AbstractText></Abstract></Article></MedlineCitation><PubmedData><ArticleIdList><ArticleId IdType='doi'>10.1000/trial</ArticleId></ArticleIdList></PubmedData></PubmedArticle></PubmedArticleSet>";
        List<Article> articles = ReflectionTestUtils.invokeMethod(new PubMedService(), "parseXml", xml);
        assertEquals("METHODS: 100 participants\nRESULTS: No difference", articles.get(0).getAbstractText());
        assertEquals("10.1000/trial", articles.get(0).getDoi());
    }
    @Test void malformedResponseDoesNotMasqueradeAsZeroArticles() {
        assertThrows(IllegalArgumentException.class, () -> ReflectionTestUtils.invokeMethod(new PubMedService(), "parseXml", "<broken>"));
    }
    @Test void allAffiliationEmailsAreSavedWithAuthorAssociationAndSource() {
        String xml = "<PubmedArticleSet><PubmedArticle><MedlineCitation><PMID>123</PMID><Article><ArticleTitle>Trial</ArticleTitle><AuthorList><Author><ForeName>Alice</ForeName><LastName>Chen</LastName><AffiliationInfo><Affiliation>University A. Electronic address: alice@example.edu; lab@example.edu.</Affiliation></AffiliationInfo></Author><Author><ForeName>Bob</ForeName><LastName>Li</LastName><AffiliationInfo><Affiliation>University A. lab@example.edu.</Affiliation></AffiliationInfo></Author></AuthorList></Article></MedlineCitation></PubmedArticle></PubmedArticleSet>";
        List<Article> articles = ReflectionTestUtils.invokeMethod(new PubMedService(), "parseXml", xml);
        JsonNode contacts = new ObjectMapper().findAndRegisterModules().valueToTree(articles.get(0)).path("emailContacts");
        assertEquals(2, contacts.size()); assertEquals("alice@example.edu", contacts.get(0).path("email").asText()); assertEquals("Alice Chen", contacts.get(0).path("authorNames").get(0).asText());
        assertEquals("https://pubmed.ncbi.nlm.nih.gov/123/", contacts.get(0).path("sourceUrl").asText());
        assertEquals(2, contacts.get(1).path("authorNames").size()); assertTrue(contacts.get(0).path("sourceSnippet").asText().contains("alice@example.edu"));
    }
    @Test void abstractEmailIsPreservedWithoutGuessingOwnerAndMissingEmailIsAnEmptyList() {
        String xml = "<PubmedArticleSet><PubmedArticle><MedlineCitation><PMID>456</PMID><Article><ArticleTitle>Trial</ArticleTitle><Abstract><AbstractText>Contact: team@example.edu.</AbstractText></Abstract></Article></MedlineCitation></PubmedArticle><PubmedArticle><MedlineCitation><PMID>789</PMID><Article><ArticleTitle>Another trial</ArticleTitle></Article></MedlineCitation></PubmedArticle></PubmedArticleSet>";
        List<Article> articles = ReflectionTestUtils.invokeMethod(new PubMedService(), "parseXml", xml);
        JsonNode first = new ObjectMapper().findAndRegisterModules().valueToTree(articles.get(0)).path("emailContacts"), second = new ObjectMapper().findAndRegisterModules().valueToTree(articles.get(1)).path("emailContacts");
        assertEquals("team@example.edu", first.path(0).path("email").asText()); assertTrue(first.path(0).path("authorNames").isEmpty());
        assertTrue(second.isArray()); assertTrue(second.isEmpty());
    }
}
