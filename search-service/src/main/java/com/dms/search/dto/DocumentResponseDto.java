package com.dms.search.dto;

import com.dms.search.enums.CourseLevel;
import com.dms.search.enums.DocumentCategory;
import com.dms.search.enums.DocumentType;
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

    private String filename;

    private String content;

    private String userId;

    private Long fileSize;

    private String mimeType;

    private DocumentType documentType;

    private String major;

    private String courseCode;

    private CourseLevel courseLevel;

    private DocumentCategory category;

    private Set<String> tags;

    private Map<String, String> extractedMetadata;

    private Date createdAt;

    private List<String> highlights;

}
