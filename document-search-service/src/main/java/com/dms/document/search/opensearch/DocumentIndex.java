package com.dms.document.search.opensearch;

import com.dms.document.search.enums.DocumentReportStatus;
import com.dms.document.search.enums.DocumentStatus;
import com.dms.document.search.enums.DocumentType;
import com.dms.document.search.enums.SharingType;
import lombok.Builder;
import lombok.Data;

import java.util.Date;
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
    private Set<String> majors;
    private Set<String> courseCodes;
    private String courseLevel;
    private Set<String> categories;
    private Set<String> tags;
    private String userId;
    private SharingType sharingType;
    private Set<String> sharedWith;
    private boolean deleted;
    private DocumentStatus status;
    private String language;
    private Date createdAt;
    private DocumentReportStatus reportStatus;
    private Integer recommendationCount;
    private Integer favoriteCount;
    private Integer currentVersion;
}