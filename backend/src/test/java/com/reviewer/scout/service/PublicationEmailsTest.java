package com.reviewer.scout.service;

import com.reviewer.scout.util.PublicationEmails;
import org.junit.jupiter.api.Test;
import com.reviewer.scout.entity.PublicationEmail;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PublicationEmailsTest {
    @Test void apostrophesAndTagsArePreservedRatherThanTruncatedToAnotherAddress() {
        var emails = PublicationEmails.extract("Electronic address: anne.o'brien+clinical@university.edu.", "Anne O'Brien", "https://pubmed.ncbi.nlm.nih.gov/123/", "PubMed 作者单位");
        assertEquals(1, emails.size()); assertEquals("anne.o'brien+clinical@university.edu", emails.get(0).email());
    }
    @Test void unsupportedUnicodeLocalPartsCannotProduceAnAsciiSuffixAddress() {
        assertTrue(PublicationEmails.extract("Email: éanne@example.edu", null, "https://pubmed.ncbi.nlm.nih.gov/123/", "文献摘要").isEmpty());
    }
    @Test void aPreviouslyTruncatedAddressCannotSurviveRefreshThroughMerge() {
        var truncated = new PublicationEmail("brien@example.edu", List.of("Anne O'Brien"), "https://pubmed.ncbi.nlm.nih.gov/123/", "PubMed 作者单位", "Electronic address: anne.o'brien@example.edu.");
        var fresh = PublicationEmails.extract(truncated.sourceSnippet(), "Anne O'Brien", truncated.sourceUrl(), truncated.sourceLabel());
        var merged = PublicationEmails.merge(List.of(truncated), fresh);
        assertEquals(1, merged.size()); assertEquals("anne.o'brien@example.edu", merged.get(0).email());
    }
}
