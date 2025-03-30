package com.dms.document.search.dto;

import com.dms.document.search.enums.DocumentType;
import lombok.Builder;
import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Builder
@Data
public class DocumentResponseDto {
    private String id;

    private String status;

    private String filename;

    private String content;

    private String userId;

    private Long fileSize;

    private String mimeType;

    private DocumentType documentType;

    private Set<String> majors;

    private Set<String> courseCodes;

    private String courseLevel;

    private Set<String> categories;

    private Set<String> tags;

    private Map<String, String> extractedMetadata;

    private String language;

    private Date createdAt;

    private List<String> highlights;

}
