package com.reviewer.scout.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.*;
import com.reviewer.scout.util.PublicationEmails;

@Data
@Entity
@Table(name = "article", uniqueConstraints = @UniqueConstraint(columnNames = {"scan_run_id", "pmid"}))
public class Article {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "scan_run_id", nullable = false)
    private Long scanRunId;
    @Column(nullable = false, length = 20)
    private String pmid;
    private Integer searchRank;
    @Column(length = 100)
    private String doi;
    @Column(columnDefinition = "TEXT")
    private String title;
    @Column(columnDefinition = "MEDIUMTEXT")
    private String abstractText;
    @Column(columnDefinition = "TEXT")
    private String authors;
    @Convert(converter = PublicationEmailConverter.class)
    @Column(name = "email_contacts", columnDefinition = "MEDIUMTEXT")
    private List<PublicationEmail> emailContacts = new ArrayList<>();
    private LocalDateTime emailCheckedAt;

    public List<PublicationEmail> getEmailContacts() {
        String url = sourceUrl == null || sourceUrl.isBlank() ? "https://pubmed.ncbi.nlm.nih.gov/" + pmid + "/" : sourceUrl;
        return PublicationEmails.merge(emailContacts, PublicationEmails.extract(abstractText, null, url, "文献摘要"));
    }
    @Column(name = "first_author", columnDefinition = "VARCHAR(300)")
    private String firstAuthor;
    @Column(columnDefinition = "VARCHAR(500)")
    private String journal;
    @Column(name = "journal_abbrev", columnDefinition = "VARCHAR(200)")
    private String journalAbbrev;
    @Column(length = 20)
    private String issn;
    @Column(columnDefinition = "VARCHAR(20)")
    private String pubdate;
    @Column(name = "pubtypes", columnDefinition = "VARCHAR(500)")
    private String pubtypes;
    @Column(length = 10)
    private String lang;
    @Column(name = "is_retracted")
    private Boolean isRetracted;
    @Column(name = "mesh_terms", columnDefinition = "TEXT")
    private String meshTerms;
    @Column(name = "source_url", columnDefinition = "VARCHAR(300)")
    private String sourceUrl;
    @Column(name = "retrieved_at")
    private LocalDateTime retrievedAt;
}
