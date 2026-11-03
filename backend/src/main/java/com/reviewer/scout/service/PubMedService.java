package com.reviewer.scout.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviewer.scout.entity.Article;
import com.reviewer.scout.entity.PublicationEmail;
import com.reviewer.scout.util.PublicationEmails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;

@Service
@Slf4j
public class PubMedService {

    @Value("${pubmed.base-url}")
    private String baseUrl;
    @Value("${pubmed.tool}")
    private String tool;
    @Value("${pubmed.email}")
    private String email;
    @Value("${pubmed.min-interval-ms}")
    private long minInterval;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private long lastCall = 0;

    private synchronized void rateLimit() {
        long now = System.currentTimeMillis();
        long delta = now - lastCall;
        if (delta < minInterval) {
            try { Thread.sleep(minInterval - delta); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        lastCall = System.currentTimeMillis();
    }

    public String search(String term, int retmax) throws Exception {
        return search(term, retmax, "relevance");
    }

    public String search(String term, int retmax, String sort) throws Exception {
        rateLimit();
        String url = baseUrl + "/esearch.fcgi?db=pubmed&term=" + URLEncoder.encode(term, StandardCharsets.UTF_8)
            + "&retmax=" + retmax + "&retmode=json&sort=" + sort + "&tool=" + tool + "&email=" + email;
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(45)).GET().build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new IllegalStateException("PubMed 暂时不可用（HTTP " + resp.statusCode() + "）");
        JsonNode root = mapper.readTree(resp.body());
        return root.path("esearchresult").path("idlist").toString();
    }

    public int count(String term) throws Exception {
        return ((Number) preview(term, "relevance").get("total")).intValue();
    }

    public Map<String, Object> preview(String term, String sort) throws Exception {
        rateLimit();
        String url = baseUrl + "/esearch.fcgi?db=pubmed&term=" + URLEncoder.encode(term, StandardCharsets.UTF_8)
            + "&retmax=0&retmode=json&sort=" + sort + "&tool=" + tool + "&email=" + email;
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(45)).GET().build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new IllegalStateException("PubMed 暂时不可用（HTTP " + resp.statusCode() + "）");
        JsonNode root = mapper.readTree(resp.body());
        JsonNode result = root.path("esearchresult");
        if (!result.has("count")) throw new IllegalStateException("PubMed 返回的数据不完整，请稍后重试");
        List<String> warnings = new ArrayList<>();
        for (String field : List.of("warninglist", "errorlist")) {
            result.path(field).fields().forEachRemaining(entry -> {
                if (entry.getValue().isArray()) entry.getValue().forEach(v -> warnings.add(v.asText()));
                else warnings.add(entry.getValue().asText());
            });
        }
        return Map.of("total", result.path("count").asInt(), "translation", result.path("querytranslation").asText(term), "warnings", warnings);
    }

    public List<Article> fetchArticles(List<String> pmids, java.util.function.BiConsumer<Integer, Integer> batchCallback) throws Exception {
        List<Article> results = new ArrayList<>();
        int totalBatches = (pmids.size() + 99) / 100;
        for (int i = 0; i < pmids.size(); i += 100) {
            rateLimit();
            List<String> batch = pmids.subList(i, Math.min(i + 100, pmids.size()));
            String idStr = String.join(",", batch);
            String body = "db=pubmed&id=" + idStr + "&retmode=xml&rettype=abstract&tool=" + tool + "&email=" + email;
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/efetch.fcgi"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) throw new IllegalStateException("PubMed 文献获取失败（HTTP " + resp.statusCode() + "）");
            List<Article> parsed = parseXml(resp.body());
            results.addAll(parsed);
            int batchNum = (i / 100) + 1;
            log.info("Fetched batch {} of {}, {} articles", batchNum, totalBatches, batch.size());
            if (batchCallback != null) {
                batchCallback.accept(batchNum, totalBatches);
            }
        }
        return results;
    }

    public List<Article> fetchArticles(List<String> pmids) throws Exception {
        return fetchArticles(pmids, null);
    }

    private Article parseArticleFromPubmed(Element pubmedArticle) {
        NodeList medlineNodes = pubmedArticle.getElementsByTagName("MedlineCitation");
        if (medlineNodes.getLength() == 0) return null;
        Element medline = (Element) medlineNodes.item(0);
        Article art = parseArticle(medline);
        if (art != null) {
            NodeList ids = pubmedArticle.getElementsByTagName("ArticleId");
            for (int i = 0; i < ids.getLength(); i++) {
                Element id = (Element) ids.item(i);
                if ("doi".equals(id.getAttribute("IdType"))) art.setDoi(id.getTextContent().trim());
            }
        }
        return art;
    }

    private Article parseArticle(Element citation) {
        Article art = new Article();
        art.setPmid(getText(citation, "PMID"));
        if (art.getPmid().isEmpty()) return null;

        Element article = getElement(citation, "Article");
        if (article == null) return null;

        art.setTitle(getText(article, "ArticleTitle"));

        StringBuilder ab = new StringBuilder();
        NodeList absNodes = article.getElementsByTagName("AbstractText");
        for (int i = 0; i < absNodes.getLength(); i++) {
            Element absEl = (Element) absNodes.item(i);
            String label = absEl.getAttribute("Label");
            String text = absEl.getTextContent().trim();
            if (!text.isEmpty()) {
                if (!label.isEmpty()) ab.append(label).append(": ");
                ab.append(text).append("\n");
            }
        }
        art.setAbstractText(ab.toString().trim());

        List<String> authors = new ArrayList<>();
        NodeList authorNodes = article.getElementsByTagName("Author");
        List<PublicationEmail> contacts = new ArrayList<>();
        for (int i = 0; i < authorNodes.getLength(); i++) {
            Element a = (Element) authorNodes.item(i);
            String last = getChildText(a, "LastName");
            String fore = getChildText(a, "ForeName");
            String name = (fore + " " + last).trim();
            if (name.isEmpty()) {
                name = getChildText(a, "CollectiveName");
            }
            if (!name.isEmpty()) authors.add(name);
            NodeList affNodes = a.getElementsByTagName("Affiliation");
            for (int j = 0; j < affNodes.getLength(); j++) {
                String aff = affNodes.item(j).getTextContent();
                contacts = PublicationEmails.merge(contacts, PublicationEmails.extract(aff, name, "https://pubmed.ncbi.nlm.nih.gov/" + art.getPmid() + "/", "PubMed 作者单位"));
            }
        }
        art.setAuthors(String.join("; ", authors));
        art.setFirstAuthor(authors.isEmpty() ? "" : authors.get(0));
        art.setEmailContacts(contacts);

        Element journal = getElement(article, "Journal");
        if (journal != null) {
            art.setJournal(getText(journal, "Title"));
            art.setJournalAbbrev(getText(journal, "ISOAbbreviation"));
            Element issn = getElement(journal, "ISSN");
            art.setIssn(issn != null ? issn.getTextContent() : "");
            Element pubDate = getElement(journal, "PubDate");
            if (pubDate != null) {
                String year = getChildText(pubDate, "Year");
                String month = getChildText(pubDate, "Month");
                art.setPubdate(year + (month.isEmpty() ? "" : "-" + monthMap(month)));
            }
        }

        List<String> pubtypes = new ArrayList<>();
        NodeList ptNodes = article.getElementsByTagName("PublicationType");
        for (int i = 0; i < ptNodes.getLength(); i++) {
            pubtypes.add(ptNodes.item(i).getTextContent().trim());
        }
        art.setPubtypes(String.join("; ", pubtypes));
        art.setIsRetracted(pubtypes.stream().anyMatch(p -> p.toLowerCase().contains("retracted publication")));

        art.setLang(getText(article, "Language"));

        // Parse MeSH terms from MedlineCitation
        NodeList meshNodes = citation.getElementsByTagName("DescriptorName");
        List<String> meshTerms = new ArrayList<>();
        for (int i = 0; i < meshNodes.getLength(); i++) {
            String term = meshNodes.item(i).getTextContent().trim();
            if (!term.isEmpty()) meshTerms.add(term);
        }
        art.setMeshTerms(String.join("|", meshTerms));

        NodeList doiNodes = article.getElementsByTagName("ArticleId");
        for (int i = 0; i < doiNodes.getLength(); i++) {
            Element el = (Element) doiNodes.item(i);
            if ("doi".equals(el.getAttribute("IdType"))) {
                art.setDoi(el.getTextContent().trim());
                break;
            }
        }

        art.setSourceUrl("https://pubmed.ncbi.nlm.nih.gov/" + art.getPmid() + "/");
        art.setRetrievedAt(LocalDateTime.now());
        art.setEmailCheckedAt(art.getRetrievedAt());
        return art;
    }

    private String monthMap(String month) {
        try { return String.format("%02d", Integer.parseInt(month)); } catch (NumberFormatException e) {}
        String[] names = {"jan","feb","mar","apr","may","jun","jul","aug","sep","oct","nov","dec"};
        String lower = month.toLowerCase();
        for (int i = 0; i < names.length; i++) if (lower.startsWith(names[i])) return String.format("%02d", i + 1);
        return "";
    }

    private List<Article> parseXml(String xml) {
        List<Article> results = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                @Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
                @Override public void error(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
            });
            Document doc = builder.parse(new org.xml.sax.InputSource(new StringReader(xml)));
            NodeList citations = doc.getElementsByTagName("PubmedArticle");
            for (int i = 0; i < citations.getLength(); i++) {
                Element citation = (Element) citations.item(i);
                Article art = parseArticleFromPubmed(citation);
                if (art != null) results.add(art);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("PubMed XML 数据无法解析，请重试", e);
        }
        return results;
    }

    private Element getElement(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        return nodes.getLength() > 0 ? (Element) nodes.item(0) : null;
    }

    private String getText(Element parent, String tagName) {
        Element el = getElement(parent, tagName);
        return el != null ? el.getTextContent().trim() : "";
    }

    private String getChildText(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el && el.getTagName().equals(tagName)) {
                return el.getTextContent().trim();
            }
        }
        return "";
    }
}
