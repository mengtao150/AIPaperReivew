package com.reviewer.scout.entity;

import java.util.List;

/** An address observed in a publication, not an inferred correspondence role. */
public record PublicationEmail(String email, List<String> authorNames, String sourceUrl, String sourceLabel, String sourceSnippet) { }
