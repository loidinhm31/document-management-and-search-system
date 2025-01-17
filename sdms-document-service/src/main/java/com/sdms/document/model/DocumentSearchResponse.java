package com.sdms.document.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class DocumentSearchResponse {
    private List<DocumentSearchItem> content;
    private long totalElements;
    private int totalPages;
    private int pageNumber;
    private int pageSize;
    private boolean first;
    private boolean last;

    @Data
    @Builder
    public static class DocumentSearchItem {
        private String id;
        private String filename;
        private String content;
        private String userId;
        private String contentType;
        private Map<String, String> metadata;
        private float score;
    }
}