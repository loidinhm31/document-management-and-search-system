package com.dms.search.elasticsearch;

import com.dms.search.enums.CourseLevel;
import com.dms.search.enums.DocumentCategory;
import com.dms.search.enums.DocumentType;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.util.Date;
import java.util.Map;
import java.util.Set;

@Builder
@Data
@Document(indexName = "documents")
public class DocumentIndex {
    @Id
    private String id;

    @MultiField(
            mainField = @Field(type = FieldType.Text),
            otherFields = {
                    @InnerField(suffix = "raw", type = FieldType.Keyword)
            }
    )
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