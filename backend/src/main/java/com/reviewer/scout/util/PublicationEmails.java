package com.reviewer.scout.util;

import com.reviewer.scout.entity.PublicationEmail;
import java.util.*;
import java.util.regex.*;

public final class PublicationEmails {
    private static final String LOCAL_ATOM = "[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+";
    private static final Pattern EMAIL = Pattern.compile(
        "(?<![\\p{L}\\p{N}.!#$%&'*+/=?^_`{|}~\"-])" + LOCAL_ATOM + "(?:\\." + LOCAL_ATOM + ")*"
        + "@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)*"
        + "\\.[A-Za-z]{2,}(?![\\p{L}\\p{N}_@-])");
    private PublicationEmails() { }
    public static List<PublicationEmail> extract(String text, String authorName, String sourceUrl, String sourceLabel) {
        if (text == null || text.isBlank()) return List.of();
        List<PublicationEmail> contacts = new ArrayList<>(); Matcher matcher = EMAIL.matcher(text);
        while (matcher.find()) {
            List<String> names = authorName == null || authorName.isBlank() ? List.of() : List.of(authorName.trim());
            int from = Math.max(0, matcher.start() - 120), to = Math.min(text.length(), matcher.end() + 120);
            contacts.add(new PublicationEmail(matcher.group(), names, sourceUrl, sourceLabel, text.substring(from, to).trim()));
        }
        return merge(contacts, List.of());
    }
    public static List<PublicationEmail> merge(List<PublicationEmail> first, List<PublicationEmail> second) {
        Map<String, PublicationEmail> result = new LinkedHashMap<>();
        for (List<PublicationEmail> group : Arrays.asList(first, second)) {
            if (group == null) continue;
            for (PublicationEmail contact : group) {
                if (contact == null || contact.email() == null || !EMAIL.matcher(contact.email()).matches()) continue;
                if (contact.sourceSnippet() != null && !contact.sourceSnippet().isBlank()
                    && EMAIL.matcher(contact.sourceSnippet()).results().noneMatch(match -> match.group().equalsIgnoreCase(contact.email()))) continue;
                String key = contact.email().toLowerCase(Locale.ROOT); PublicationEmail existing = result.get(key);
                List<String> names = new ArrayList<>();
                if (existing != null && existing.authorNames() != null) names.addAll(existing.authorNames());
                if (contact.authorNames() != null) for (String name : contact.authorNames()) if (name != null && !name.isBlank() && !names.contains(name)) names.add(name);
                PublicationEmail source = existing == null ? contact : existing;
                result.put(key, new PublicationEmail(source.email(), List.copyOf(names), source.sourceUrl(), source.sourceLabel(), source.sourceSnippet()));
            }
        }
        return new ArrayList<>(result.values());
    }
}
