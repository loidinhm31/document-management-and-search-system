package com.sdms.document.elasticsearch;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Date;
import java.util.Map;

@Data
@Builder
@Document(indexName = "documents")
public class DocumentIndex {
    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String filename;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String content;

    @Field(type = FieldType.Keyword)
    private String userId;

    @Field(type = FieldType.Keyword)
    private String contentType;

    @Field(type = FieldType.Long)
    private Long fileSize;

    @Field(type = FieldType.Date)
    private Date createdAt;

    @Field(type = FieldType.Object)
    private Map<String, String> metadata;
}