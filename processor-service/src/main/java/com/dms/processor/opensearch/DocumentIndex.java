package com.dms.processor.opensearch;

import com.dms.processor.enums.DocumentStatus;
import com.dms.processor.enums.DocumentType;
import com.dms.processor.enums.DocumentReportStatus;
import com.dms.processor.enums.SharingType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Builder
@Data
public class DocumentIndex {
    private String id;
    private String filename;
    private String content;
    private Long fileSize;
    private String mimeType;
    private DocumentType documentType;
    private String summary;
    private String major;
    private String courseCode;
    private String courseLevel;
    private String category;
    private Set<String> tags;
    private Map<String, String> extractedMetadata;
    private String userId;
    private SharingType sharingType;
    private Set<String> sharedWith;
    private boolean deleted;
    private DocumentStatus status;
    private String language;
    private Instant createdAt;
    private DocumentReportStatus reportStatus;
    private Integer recommendationCount;
}