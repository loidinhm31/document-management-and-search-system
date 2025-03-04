package com.dms.document.search.model;

import com.dms.document.search.enums.DocumentReportStatus;
import com.dms.document.search.enums.DocumentStatus;
import com.dms.document.search.enums.DocumentType;
import com.dms.document.search.enums.SharingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.Map;
import java.util.Set;


@Document(collection = "documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentInformation {
    @Id
    private String id;

    @Field("status")
    private DocumentStatus status;

    @Field("summary")
    private String summary;

    @Indexed
    @Field("document_type")
    private DocumentType documentType;

    @Indexed
    @Field("majors")
    private Set<String> majors;

    @Indexed
    @Field("course_codes")
    private Set<String> courseCodes;

    @Indexed
    @Field("course_level")
    private String courseLevel;

    @Indexed
    @Field("categories")
    private Set<String> categories;

    @Indexed
    @Field("tags")
    private Set<String> tags;

    @Field("user_id")
    private String userId;

    @Field("sharing_type")
    private SharingType sharingType;

    @Field("shared_with")
    private Set<String> sharedWith;

    @Field("deleted")
    private boolean deleted;

    @Field("current_version")
    private Integer currentVersion;

    // Version fields
    @Field("processing_error")
    private String processingError;

    @Field("filename")
    private String filename;

    @Field("file_path")
    private String filePath;

    @Field("thumbnail_path")
    private String thumbnailPath;

    @Field("file_size")
    private Long fileSize;

    @Field("mime_type")
    private String mimeType;

    @Field("extracted_metadata")
    private Map<String, String> extractedMetadata;

    @Field("content")
    private String content;

    @Field("language")
    private String language;

    // Audit fields
    @Field("created_at")
    private Instant createdAt;

    @Field("updated_at")
    private Instant updatedAt;

    @Field("created_by")
    private String createdBy;

    @Field("updated_by")
    private String updatedBy;

    @Field("report_status")
    private DocumentReportStatus reportStatus;

    @Field("recommendation_count")
    private Integer recommendationCount;
}