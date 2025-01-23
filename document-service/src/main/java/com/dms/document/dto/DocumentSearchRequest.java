package com.dms.document.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentSearchRequest {
    private String search;
    private String major;
    private String level;
    private String category;
    private String sortField;
    private String sortDirection;
    private int page;
    private int size;
}