package com.reviewer.scout.service;

import lombok.Data;
import java.time.LocalDate;

@Data
public class SearchCriteria {
    private String topic;
    private String query;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private String articleTypes = "";
    private String language = "";
    private String dateField = "dp";
    private String textAvailability = "any";
    private String sortOrder = "relevance";
}
