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
public class DocumentSearchRequest {
    private String search;
    private Set<String> majors;
    private Set<String> courseCodes;
    private String level;
    private Set<String> categories;
    private Boolean favoriteOnly;
    private String sortField;
    private String sortDirection;
    private Set<String> tags;
    private int page;
    private int size;
}