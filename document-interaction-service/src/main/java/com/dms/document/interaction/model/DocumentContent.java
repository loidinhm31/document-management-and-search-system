package com.dms.document.interaction.model;


import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Map;

@Data
@Builder
@Document(collection = "document_contents")
public class DocumentContent {
    @Id
    private String id;

    @Indexed
    @Field("document_id")
    private String documentId;

    @Indexed
    @Field("version_number")
    private Integer versionNumber;

    @Field("content")
    private String content;

    @Field("extracted_metadata")
    private Map<String, String> extractedMetadata;

    @Version
    private Long version;
}