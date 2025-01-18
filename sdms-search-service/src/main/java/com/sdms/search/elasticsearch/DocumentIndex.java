package com.sdms.search.elasticsearch;

import com.sdms.search.enums.CourseLevel;
import com.sdms.search.enums.DocumentCategory;
import com.sdms.search.enums.DocumentType;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Date;
import java.util.Map;
import java.util.Set;

@Builder
@Data
@Document(indexName = "documents")
public class DocumentIndex {
    @Id
    private String id;

    @Field(type = FieldType.Text)
    private String filename;

    @Field(type = FieldType.Text)
    private String content;

    @Field(type = FieldType.Keyword)
    private String userId;

    @Field(type = FieldType.Long)
    private Long fileSize;

    @Field(type = FieldType.Keyword)
    private String mimeType;

    @Field(type = FieldType.Keyword)
    private DocumentType documentType;

    @Field(type = FieldType.Keyword)
    private String major;

    @Field(type = FieldType.Keyword)
    private String courseCode;

    @Field(type = FieldType.Keyword)
    private CourseLevel courseLevel;

    @Field(type = FieldType.Keyword)
    private DocumentCategory category;

    @Field(type = FieldType.Keyword)
    private Set<String> tags;

    @Field(type = FieldType.Object)
    private Map<String, String> extractedMetadata;

    @Field(type = FieldType.Date)
    private Date createdAt;
}