package com.reviewer.scout.entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import java.util.*;

@Converter
public class PublicationEmailConverter implements AttributeConverter<List<PublicationEmail>, String> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    @Override public String convertToDatabaseColumn(List<PublicationEmail> emails) {
        try { return MAPPER.writeValueAsString(emails == null ? List.of() : emails); }
        catch (Exception e) { throw new IllegalArgumentException("联系邮箱无法保存", e); }
    }
    @Override public List<PublicationEmail> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try { return MAPPER.readValue(json, new TypeReference<List<PublicationEmail>>() { }); }
        catch (Exception e) { throw new IllegalArgumentException("联系邮箱数据无法解析", e); }
    }
}
