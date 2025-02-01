package com.dms.document.interaction.dto;

import com.dms.document.interaction.enums.DocumentStatus;
import lombok.Builder;
import lombok.Data;

import java.util.Date;
import java.util.Map;

@Data
@Builder
public class DocumentVersionDetail {
    private Integer versionNumber;
    private String filePath;
    private String originalFilename;
    private Long fileSize;
    private String mimeType;
    private DocumentStatus status;
    private String language;
    private Map<String, String> extractedMetadata;
    private String processingError;
    private String createdBy;
    private Date createdAt;
}