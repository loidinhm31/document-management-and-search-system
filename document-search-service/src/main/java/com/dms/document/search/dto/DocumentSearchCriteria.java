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
public class DocumentSearchCriteria {
    private String search;
    private Set<String> majors;
    private Set<String> courseCodes;
    private String level;
    private Set<String> categories;
    private Set<String> tags;
    private String sortField;
    private String sortDirection;

    public String getSortField() {
        return sortField != null ? sortField : "createdAt";
    }

    public String getSortDirection() {
        return sortDirection != null ? sortDirection : "DESC";
    }
}