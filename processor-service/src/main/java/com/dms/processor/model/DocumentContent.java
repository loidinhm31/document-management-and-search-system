package com.dms.processor.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

@Data
@Builder
@Document(collection = "document_contents")
public class DocumentContent {
    @Id
    private String id;

    @Indexed
    private String documentId;

    @Indexed
    private Integer versionNumber;

    private String content;
    private Map<String, String> extractedMetadata;

    @Version
    private Long version;
}