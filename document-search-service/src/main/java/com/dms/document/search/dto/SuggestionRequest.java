package com.dms.document.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuggestionRequest {
    private String query;
    private String major;
    private String level;
    private String category;
    private Set<String> tags;
}