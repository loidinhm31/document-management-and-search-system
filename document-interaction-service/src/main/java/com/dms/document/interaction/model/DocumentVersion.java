package com.dms.document.interaction.model;


import com.dms.document.interaction.enums.DocumentStatus;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;
import java.util.Map;

@Data
@Builder
public class DocumentVersion {
    @Field("version_number")
    private Integer versionNumber;

    @Field("file_path")
    private String filePath;

    @Field("thumbnail_path")
    private String thumbnailPath;

    @Field("filename")
    private String filename;

    @Field("file_size")
    private Long fileSize;

    @Field("mime_type")
    private String mimeType;

    @Field("status")
    private DocumentStatus status;

    @Field("language")
    private String language;

    @Field("extracted_metadata")
    private Map<String, String> extractedMetadata;

    @Field("processing_error")
    private String processingError;

    @Field("created_by")
    private String createdBy;

    @Field("created_at")
    private Date createdAt;
}