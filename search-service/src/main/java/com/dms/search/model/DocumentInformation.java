package com.dms.search.model;

import com.dms.search.enums.DocumentType;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@Document(collection = "documents")
public class DocumentInformation {
    @Id
    private String id;

    @Field("filename")
    private String filename;

    @Field("original_filename")
    private String originalFilename;

    @Field("file_path")
    private String filePath;

    @Field("file_size")
    private Long fileSize;

    @Field("mime_type")
    private String mimeType;

    @Indexed
    @Field("document_type")
    private DocumentType documentType;

    @Field("content")
    private String content;

    @Indexed
    @Field("major")
    private String major;

    @Indexed
    @Field("course_code")
    private String courseCode;

    @Indexed
    @Field("course_level")
    private String courseLevel;

    @Indexed
    @Field("category")
    private String category;

    @Indexed
    @Field("tags")
    private Set<String> tags;

    @Field("extracted_metadata")
    private Map<String, String> extractedMetadata;

    @Field("user_id")
    private String userId;

    @Field("created_at")
    private Date createdAt;

    @Field("updated_at")
    private Date updatedAt;

    @Field("created_by")
    private String createdBy;

    @Field("updated_by")
    private String updatedBy;

    @Field("deleted")
    private boolean deleted;
}