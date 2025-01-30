package com.dms.document.search.dto;

import lombok.Builder;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.util.Set;

@Data
@Builder
public class DocumentSearchCriteria {
    private String search;
    private String major;
    private String level;
    private String category;
    private Set<String> tags;
    private String sortField;
    private String sortDirection;

    public String getSortField() {
        // Default to createdAt if not specified
        return StringUtils.hasText(sortField) ? sortField : "createdAt";
    }

    public String getSortDirection() {
        // Default to descending if not specified
        if (!StringUtils.hasText(sortDirection)) {
            return "desc";
        }
        // Validate direction
        String normalizedDirection = sortDirection.toLowerCase();
        return normalizedDirection.equals("asc") ? "asc" : "desc";
    }
}