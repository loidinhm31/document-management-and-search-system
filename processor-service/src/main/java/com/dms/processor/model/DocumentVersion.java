package com.dms.processor.model;


import com.dms.processor.enums.DocumentStatus;
import com.dms.processor.enums.DocumentType;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;

@Data
@Builder
public class DocumentVersion {
    @Field("version_number")
    private Integer versionNumber;

    @Field("file_path")
    private String filePath;

    @Field("thumbnail_path")
    private String thumbnailPath;

    @Field("original_filename")
    private String originalFilename;

    @Field("file_size")
    private Long fileSize;

    @Field("mime_type")
    private String mimeType;

    @Field("document_type")
    private DocumentType documentType;

    @Field("status")
    private DocumentStatus status;

    @Field("language")
    private String language;

    @Field("processing_error")
    private String processingError;

    @Field("created_by")
    private String createdBy;

    @Field("created_at")
    private Date createdAt;
}